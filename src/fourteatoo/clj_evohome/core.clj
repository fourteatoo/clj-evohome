(ns fourteatoo.clj-evohome.core
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.set :as set]
   [fourteatoo.clj-evohome.api :as api]))


(def ^:private installation-ttl (* 15 60 1000))

(defn- location-name [location]
  (get-in location [:location-info :name]))

(defn- location-id [location]
  (get-in location [:location-info :location-id]))

(defn- location-temperature-control-systems [location]
  (->> (:gateways location)
       (mapcat :temperature-control-systems)))

(defn- index-zones [installation]
  (reduce (fn [m location]
            (assoc m (location-name location)
                   (->> (:gateways location)
                        (mapcat (fn [gateway]
                                  (mapcat (fn [tcs]
                                            (map (juxt :name identity)
                                                 (:zones tcs)))
                                          (:temperature-control-systems gateway))))
                        (into {}))))
          {} installation))

(defn- index-locations [installation k]
  (reduce (fn [m location]
            (assoc m (k location)
                   location))
          {} installation))

(defn- index-installation [installation]
  (let [locations-by-name (index-locations installation location-name)
        locations-by-id (index-locations installation location-id)
        zones (index-zones installation)]
    {:locations-by-id locations-by-id
     :locations-by-name locations-by-name
     :zones zones}))

(defn- *nix-time []
  (System/currentTimeMillis))

(defn cached
  "Cache `f`'s return value for `millis` milliseconds.  `f` must be a
  function without arguments."
  [f millis]
  (let [cache (atom {:valid-until -1})]
    (fn []
      (let [now (*nix-time)
            stored @cache]
        (:value (if (< now (:valid-until stored))
                  stored
                  (reset! cache {:valid-until (+ now millis)
                                 :value (f)})))))))

(defrecord Installation [locations index])

(defn- get-installation* [c uid]
  (let [installation (api/get-installation c uid :tcs? true)]
    (->Installation installation (index-installation installation))))

(defn authenticate-client
  "Like `api/authenticate-client`.  Return an augmented `api/ApiClient`
  that can be passed to other functions of this or the `api`
  namespace."
  [username password]
  (let [client (api/authenticate-client username password)
        user-id (:user-id (api/get-user-account client))]
    (-> client
        (assoc ::user-id user-id)
        (assoc ::installation (cached #(get-installation* client user-id)
                                      installation-ttl)))))

(defn get-installation
  "Much like api/get-installation, but it may return a cached version of
  the installation, reducing the frequency of network queries for
  mostly-static data.  Unlike `api/get-installation`, return an
  Installation record with the REST API result in `:locations` and an
  indexed version of the same object tree in `:index`."
  [client]
  ((::installation client)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- path->zone-id [c path]
  (:zone-id (get-in (get-installation c) (concat [:index :zones] path))))

(defn- ->zone-id [c zone-or-path]
  (if (vector? zone-or-path)
    (path->zone-id c zone-or-path)
    zone-or-path))

(defn- system-modes [system]
  (map (comp csk/->kebab-case-keyword :system-mode)
       (:allowed-system-modes system)))

(defn- location-modes [location]
  (->> (location-temperature-control-systems location)
       (map system-modes)
       (apply set/intersection)))

(defn- assert-mode-allowed [location mode]
  (let [modes (location-modes location)]
    (when-not (contains? modes mode)
      (throw (ex-info "unknown mode for the installation"
                      {:location location-id
                       :mode mode
                       :allowed modes})))))

(defn- find-location [c name-or-id]
  (let [inst (get-installation c)]
    (or (get-in inst [:index :locations-by-name name-or-id])
        (get-in inst [:index :locations-by-id name-or-id]))))

(defn- location-temperature-control-systems [location]
  (->> (:gateways location)
       (mapcat :temperature-control-systems)))

(defn set-location-mode
  "Change the mode of all the temperature control systems at location.
  For simple installations a location has just one TCS, thus the
  location ID is enough to identify the TCS within.  Unlike
  `api/set-system-mode` this function accepts a user-defined name such
  as \"Home\" as well as a location ID."
  [c location-name-or-id mode]
  (let [loc (find-location c location-name-or-id)]
    (assert-mode-allowed loc mode)
    (->> (location-temperature-control-systems loc)
         (map :system-id)
         (run! #(api/set-system-mode c % mode)))))

(defn get-location-systems-status
  "Query all the temperature control systems at location.  Return a
  sequence of maps.  See also `api/get-system-status`."
  [client location-name-or-id]
  (let [loc (find-location client location-name-or-id)]
    (->> (location-temperature-control-systems loc)
         (map :system-id)
         (map (partial api/get-system-status client)))))

(defn set-zone-temperature
  "Set the specific zone's temperature.  Unlike
  `api/set-zone-temperature` this function accepts a pathname such
  as [\"Home\" \"Bedroom\"] as well as a zone ID."
  [client zone-path-or-id temperature & args]
  (apply api/set-zone-temperature client (->zone-id client zone-path-or-id) temperature args))

(defn cancel-zone-override
  "Cancel a previous `set-zone-temperature`.  This will effectively
  resume the normal schedule.  Unlike `api/cancel-zone-temperature`
  this function accepts a pathname such as [\"Home\" \"Bedroom\"] as
  well as a zone ID."
  [client zone-path-or-id]
  (api/cancel-zone-override client (->zone-id client zone-path-or-id)))

(defn get-zone-schedule
  "Return the daily schedule of zone with ID `zone-id`.  Unlike
  `api/get-zone-schedule` this function accepts a pathname such
  as [\"Home\" \"Bedroom\"] as well as a zone ID."
  [client zone-path-or-id]
  (api/get-zone-schedule client (->zone-id client zone-path-or-id)))

(defn set-zone-schedule
  "Set the specified `zone-id` to have the daily plan `schedule`. See
  `get-zone-schedule`.  Unlike `api/set-zone-schedule` this function
  accepts a pathname such as [\"Home\" \"Bedroom\"] as well as a zone
  ID."
  [client zone-path-or-id schedule]
  (api/get-zone-schedule client (->zone-id client zone-path-or-id)))

(defn get-location-status
  "Get the status of the specified location.  Unlike
  `api/get-location-status` this function accepts a user-defined name
  such as \"Home\" as well as a location ID."
  [client location-name-or-id & args]
  (let [loc (find-location client location-name-or-id)]
    (apply api/get-location-status client (location-id loc) args)))
