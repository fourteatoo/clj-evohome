(ns fourteatoo.clj-evohome.core
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.pprint :as pp]
   [clojure.set :as set]
   [fourteatoo.clj-evohome.api :as api]
   [clojure.string :as s]))

(def ^:private installation-ttl (* 15 60 1000))

(defn location-name
  "Return the name of the location `loc`."
  [loc]
  (get-in loc [:location-info :name]))

(defn location-id
  "Return the ID of the location `loc`."
  [loc]
  (get-in loc [:location-info :location-id]))

(defn location-temperature-control-systems
  "Return the list of the temperature control systems belonging to
  location `loc`."
  [loc]
  (->> (:gateways loc)
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

(defn- cached
  "Cache `f`'s return value for `millis` milliseconds.  `f` must be a
  function without arguments.  Return a function."
  [f millis]
  (let [cache (atom {:valid-until -1})]
    (fn []
      (let [now (*nix-time)
            stored @cache]
        (:value (if (< now (:valid-until stored))
                  stored
                  (reset! cache {:valid-until (+ now millis)
                                 :value (f)})))))))

(defrecord IndexedInstallation [locations index])

(defn- fetch-indexed-installation [c uid]
  (let [installation (api/get-installation c uid :tcs? true)]
    (->IndexedInstallation installation (index-installation installation))))

(defn- indexed-installation [c]
  ((::installation c)))

(defn authenticate-client
  "Like `api/authenticate-client`.  Return an augmented `api/ApiClient`
  that can be passed to other functions of this or the `api`
  namespace."
  [username password]
  (let [client (api/authenticate-client username password)
        user-id (:user-id (api/get-user-account client))]
    (-> client
        (assoc ::user-id user-id)
        (assoc ::installation (cached #(fetch-indexed-installation client user-id)
                                      installation-ttl)))))

(defn- path->zone-id [c path]
  (:zone-id (get-in (indexed-installation c) (concat [:index :zones] path))))

(defn- ->zone-id [c zone-or-path]
  (if (vector? zone-or-path)
    (or (path->zone-id c zone-or-path)
        (throw (ex-info "unknown zone path" {:client c :path zone-or-path})))
    zone-or-path))

(defn- system-modes [system]
  (set (map (comp csk/->kebab-case-keyword :system-mode)
            (:allowed-system-modes system))))

(defn- location-modes [location]
  (let [mode-sets (->> (location-temperature-control-systems location)
                       (map system-modes))]
    (if (next mode-sets)
      (apply set/intersection mode-sets)
      (first mode-sets))))

(defn- find-location [c name-or-id]
  (let [inst (indexed-installation c)]
    (or (if (vector? name-or-id)
          (get-in inst [:index :locations-by-name (first name-or-id)])
          (get-in inst [:index :locations-by-id name-or-id]))
        (throw (ex-info "unknown location" {:client c :location name-or-id})))))

(defn- location-temperature-control-systems [location]
  (->> (:gateways location)
       (mapcat :temperature-control-systems)))

(defn- location-zones [loc]
  (->> (location-temperature-control-systems loc)
       (mapcat :zones)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn print-installation-index
  "Print two reference tables for the installation.  One with with the
  name and ID of the locations.  The second with the name and ID of
  the zones."
  [inst]
  (println "Locations:")
  (pp/print-table (map (fn [loc]
                         {:location (location-name loc)
                          :id (location-id loc)
                          :systems (->> (location-temperature-control-systems loc)
                                        (map :system-id)
                                        (s/join ","))})
                       inst))
  (println "Zones:")
  (pp/print-table (mapcat (fn [loc]
                            (map (fn [z]
                                   {:location (location-name loc)
                                    :zone (:name z)
                                    :id (:zone-id z)})
                                 (location-zones loc)))
                          inst)))

(defn get-installation
  "Much like api/get-installation, but it may return a cached version of
  the installation, reducing the frequency of network queries for
  mostly-static data."
  [client]
  (:locations (indexed-installation client)))

(defn get-location
  "Much like api/get-location, but it may return a cached version of
  the installation, reducing the frequency of network queries for
  mostly-static data."
  [client name-or-id]
  (find-location client name-or-id))

(defn- assert-mode-allowed [location mode]
  (let [modes (location-modes location)]
    (when-not (contains? modes mode)
      (throw (ex-info "unknown mode to the installation"
                      {:location (location-id location)
                       :mode mode
                       :allowed modes})))))

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
  (api/set-zone-schedule client (->zone-id client zone-path-or-id) schedule))

(defn get-location-status
  "Get the status of the specified location.  Unlike
  `api/get-location-status` this function accepts a user-defined name
  such as \"Home\" as well as a location ID."
  [client location-name-or-id & args]
  (let [loc (find-location client location-name-or-id)]
    (apply api/get-location-status client (location-id loc) args)))
