(ns fourteatoo.clj-evohome.api
  "Interface to the current (v1) REST API."
  (:require [clojure.string :as s]
            [clojure.pprint :as pp]
            [fourteatoo.clj-evohome.http :as http]
            [cheshire.core :as json]
            [camel-snake-kebab.core :as csk]
            [java-time :as jt]))


(def ^:private host-url "https://tccna.honeywell.com")

(def ^:private api-url (str host-url "/WebAPI/emea/api/v1"))

(def ^:private auth-url (str host-url "/Auth/OAuth/Token"))

(def ^:private authorization-token
  "NGEyMzEwODktZDJiNi00MWJkLWE1ZWItMTZhMGE0MjJiOTk5OjFhMTVjZGI4LTQyZGUtNDA3Yi1hZGQwLTA1OWY5MmM1MzBjYg==")

(defn- get-auth-tokens [credentials]
  (let [tokens (-> (http/http-post auth-url
                                   {:form-params credentials
                                    :headers {:authorization (str "Basic " authorization-token)}})
                   :json)]
    (assoc tokens :expires (jt/plus (jt/local-date-time)
                                    (jt/seconds (:expires-in tokens))))))

(defn- basic-login [username password]
  (get-auth-tokens {:grant_type "password"
                    :scope "EMEA-V1-Basic EMEA-V1-Anonymous EMEA-V1-Get-Current-User-Account"
                    :Username username
                    :Password password}))

(defn- refresh-tokens [tokens]
  (get-auth-tokens {:grant_type "refresh_token"
                    :scope (:scope tokens)
                    :refresh_token (:refresh-token tokens)}))

(def authenticate-client)

(defn- ensure-fresh-client [client]
  (when (jt/before? (-> client :tokens deref :expires)
                    (jt/plus (jt/local-date-time)
                             (jt/seconds 15)))
    (try
      (swap! (:tokens client) refresh-tokens)
      client
      (catch Exception e
        (authenticate-client (:username client)
                             (:password client)))))
  client)

(defn- auth-tokens [client]
  (-> (ensure-fresh-client client)
      :tokens
      deref))

(defrecord ApiClient [username password tokens])

(defn authenticate-client [username password]
  (let [tokens (basic-login username password)]
    (->ApiClient username password (atom tokens))))

(defn- make-http-headers [client]
  (let [{:keys [access-token token-type]} (auth-tokens client)]
    {:accept "application/json"
     :authorization (str token-type " " access-token)}))

(defn- wrap-http [op]
  (fn [client path & {:as opts}]
    {:pre [(instance? ApiClient client)]}
    (:json (op (str api-url "/" path)
               (http/merge-http-opts {:headers (make-http-headers client)}
                                     opts)))))

(def ^:private http-get (wrap-http #'http/http-get))
(def ^:private http-post (wrap-http #'http/http-post))
(def ^:private http-put (wrap-http #'http/http-put))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn location-name
  "Return the name of the location `loc`."
  [loc]
  (get-in loc [:location-info :name]))

(defn location-id
  "Return the ID of the location `loc`."
  [loc]
  (get-in loc [:location-info :location-id]))

(defn- installation-locations
  "Return a list of locations matching name from those in
  `installation`."
  [name inst]
  (filter #(= name (location-name %)) inst))

(defn location-temperature-control-systems
  "Return the list of the temperature control systems belonging to
  location `loc`."
  [loc]
  (->> (:gateways loc)
       (mapcat :temperature-control-systems)))

(defn- location-zones [loc]
  (->> (location-temperature-control-systems loc)
       (mapcat :zones)))

(defn find-zone-id
  "Return the zone ID in `inst` pointed by the pair of names `location`
  `zone`."
  [location zone inst]
  (->> (installation-locations location inst)
       (mapcat location-zones)
       (filter #(= zone (:zone-name %)))
       first
       :zone-id))

(defn find-location-id
  "Return the location ID with name `name` in installation `inst`."
  [name inst]
  (->> (installation-locations name inst)
       first
       location-id))

(defn find-temperature-control-system-ids
  "Find the temperature control system IDs at `location-name` in
  installation `inst`.  Return a list."
  [location-name inst]
  (->> (installation-locations location-name inst)
       (mapcat location-temperature-control-systems)
       (map :system-id)))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-user-account
  "Query the user account basic data, such as name, surname, address,
  language, user ID, etc.  Return a map."
  [client]
  {:pre [(instance? ApiClient client)]}
  (http-get client "userAccount"))

(defn get-installation
  "Given a user ID (see `get-user-account`), query the installation
  belonging to the user.  An installation is a collection of
  locations.  The data returned for each location are name, type,
  adrress, gateways, etc.  If `tcs?` is true, include the temperature
  control systems.  Return a sequence of maps."
  [client user-id & {:keys [tcs?]}]
  {:pre [(instance? ApiClient client)]}
  (http-get client "location/installationInfo"
            :query-params {:includeTemperatureControlSystems (boolean tcs?)
                           :userId user-id}))

(defn get-location
  "Given a location ID, query the object tree belonging to that
  location.  The data returned are name, type, adrress, gateways, etc.
  If `tcs?` is true, include the temperature control systems.  Return
  a map.  See also `get-installation`."
  [client location & {:keys [tcs?]}]
  {:pre [(instance? ApiClient client)]}
  (http-get client (str "location/" location "/installationInfo")
            :query-params {:includeTemperatureControlSystems (boolean tcs?)}))

(defn get-system-status
  "Query a specific temperature control system.  A system is associated
  to a gateway and they may be the same physical thing.  Return a map."
  [client system-id]
  {:pre [(instance? ApiClient client)]}
  (http-get client (str "temperatureControlSystem/" system-id "/status")))

(defn set-system-mode
  "Set the specific system to `mode`.  The allowed system modes depend
  on the installation.  Refer to the sepcific installation's
  `:allowed-system-modes`, as returned by `get-installations`, for the
  list of permitted values.  Set it permanently if `until` is not
  specified."
  [client system-id mode & {:keys [until]}]
  {:pre [(instance? ApiClient client)]}
  (http-put client (str "temperatureControlSystem/" system-id "/mode")
            :form-params {:SystemMode (csk/->camelCaseString mode)
                          :TimeUntil (when until (str until))
                          :Permanent (nil? until)}))

(defn- zone-heat-set-point [client zone-id data]
  (http-put client (str "temperatureZone/" zone-id "/heatSetPoint")
            :content-type "application/json"
            :body data))

(defn set-zone-temperature
  "Set the specific zone's temperature.  Set it permanently if `until`
  is not specified."
  [client zone-id temperature & {:keys [until]}]
  {:pre [(instance? ApiClient client)]}
  (zone-heat-set-point client zone-id
                       {:SetpointMode (if until
                                        "TemporaryOverride"
                                        "PermanentOverride")
                        :HeatSetpointValue temperature
                        :TimeUntil (when until (str until))}))

(defn cancel-zone-override
  "Cancel a previous `set-zone-temperature`.
  This will effectively resume the normal schedule."
  [client zone-id]
  {:pre [(instance? ApiClient client)]}
  (zone-heat-set-point client zone-id
                       {:SetpointMode "FollowSchedule"
                        :HeatSetpointValue 0
                        :TimeUntil nil}))

(defn get-zone-schedule
  "Return the daily schedule of zone with ID `zone-id`"
  [client zone-id]
  {:pre [(instance? ApiClient client)]}
  (http-get client (str "temperatureZone/" zone-id "/schedule")))

(defn set-zone-schedule
  "Set the specified `zone-id` to have the daily plan `schedule`. See
  `get-zone-schedule`."
  [client zone-id schedule]
  {:pre [(instance? ApiClient client)]}
  (http-put client (str "temperatureZone/" zone-id "/schedule")
            :content-type "application/json"
            :body schedule))

(defn get-location-status
  "Get the status of the specified location `location-id`. If `tcs?` is
  true include the temperature control systems within and their
  relative zones."
  [client location-id & {:keys [tcs?]}]
  {:pre [(instance? ApiClient client)]}
  (http-get client (str "location/" location-id "/status")
            :query-params {:includeTemperatureControlSystems (boolean tcs?)}))

(defn get-domestic-hot-water
  "Get the status of the domestic hot water."
  [client dhw-id]
  {:pre [(instance? ApiClient client)]}
  (http-get client (str "domesticHotWater/" dhw-id "/status")))

(defn set-domestic-hot-water
  "Set the state of the domestic hot water.  Set it permanently if
  `until` is not specified.  If `state` is `:auto` the normal schedule
  is resumed, thus cancelling any previous setting."
  [client dhw-id state & {:keys [until]}]
  {:pre [(instance? ApiClient client)
         (keyword? state)]}
  (http-put client (str "domesticHotWater/" dhw-id "/status")
            :form-params {:Mode (cond (= state :auto) "FollowSchedule"
                                      until "TemporaryOverride"
                                      :else "PermanentOverride")
                          :State (when (not= :auto state) (name state))
                          :UntilTime (when until (str until))}))
