(ns fourteatoo.clj-evohome.api
  "Interface to the current (v1) REST API."
  (:require [clojure.string :as s]
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

(def connect)

(defn- ensure-fresh-connection [connection]
  (when (jt/before? (jt/plus (jt/local-date-time)
                             (jt/seconds 15))
                    (-> connection :tokens deref :expires))
    (try
      (swap! (:tokens connection) refresh-tokens)
      connection
      (catch Exception e
        (connect (:username connection)
                 (:password connection)))))
  connection)

(defn- auth-tokens [connection]
  (-> (ensure-fresh-connection connection)
      :tokens
      deref))

(defrecord EvoClient [username password tokens])

(defn connect [username password]
  (let [tokens (basic-login username password)]
    (->EvoClient username password (atom tokens))))

(defn- make-http-headers [connection]
  (let [{:keys [access-token token-type]} (auth-tokens connection)]
    {:accept "application/json"
     :authorization (str token-type " " access-token)}))

(defn- wrap-http [op]
  (fn [connection path & {:as opts}]
    (assert connection)
    (:json (op (str api-url "/" path)
               (http/merge-http-opts {:headers (make-http-headers connection)}
                                     opts)))))

(def ^:private http-get (wrap-http #'http/http-get))
(def ^:private http-post (wrap-http #'http/http-post))
(def ^:private http-put (wrap-http #'http/http-put))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn user-account-info
  "Query the user account basic data, such as name, surname, address,
  language, user ID, etc.  Return a map."
  [connection]
  (http-get connection "userAccount"))

(defn installations-by-user
  "Given a user-id (see `user-account-info`), query each physical
  installation belonging to that user.  The data returned for each
  installation are name, type, adrress, gateways, etc.  Return a
  sequence of maps."
  [connection user-id]
  (http-get connection "location/installationInfo"
            :query-params {:includeTemperatureControlSystems true
                           :userId user-id}))

(defn installation-by-location
  "Given a location, query the physical installation belonging to that
  location.  The data returned are name, type, adrress, gateways, etc.
  Return a map.  See also `installations-by-user`"
  [connection location]
  (http-get connection (str "location/" location "/installationInfo")
            :query-params {:includeTemperatureControlSystems true}))

(defn get-system-status
  "Query a specific temperature control system.  A system is associated
  to a gateway; they may be the same physical thing.  Return a map."
  [connection system-id]
  (http-get connection (str "temperatureControlSystem/" system-id "/status")))

(defn set-system-mode
  "Set the specific system to `mode`.  See also `list-system-modes`."
  [connection system-id mode & {:keys [until]}]
  (http-put connection (str "temperatureControlSystem/" system-id "/mode")
            :form-params {:SystemMode (csk/->camelCaseString mode)
                          :TimeUntil (when until (str until))
                          :Permanent (nil? until)}))

(defn- zone-heat-set-point [connection zone-type zone-id data]
  (http-put connection (str zone-type "/" zone-id "/heatSetPoint")
            :content-type "application/json"
            :body data))

(defn set-zone-temperature [connection zone-id temperature & {:keys [until]}]
  (zone-heat-set-point connection "temperatureZone" zone-id
                       {:SetpointMode (if until
                                        "TemporaryOverride"
                                        "PermanentOverride")
                        :HeatSetpointValue temperature
                        :TimeUntil (when until (str until))}))

(defn cancel-zone-override [connection zone-id]
  (zone-heat-set-point connection "temperatureZone" zone-id
                       {:SetpointMode "FollowSchedule"}))

(defn get-zone-schedule
  "Return the daily schedule of zone with ID `zone-id`"
  [connection zone-id]
  (http-get connection (str "temperatureZone/" zone-id "/schedule")))

(defn set-zone-schedule
  "Set the specified `zone-id` to have the daily plan `schedule`. See
  `get-zone-schedule`."
  [connection zone-id schedule]
  (http-put connection (str "temperatureZone/" zone-id "/schedule")
            :content-type "application/json"
            :body schedule))

(defn get-location-status
  "Get the status of all zones in the specified location `location-id`."
  [connection location-id]
  (http-get connection (str "location/" location-id "/status")
            :query-params {:includeTemperatureControlSystems true}))

(defn get-domestic-hot-water [connection dhw-id]
  (http-get connection (str "domesticHotWater/" dhw-id "/status")))

(defn set-domsetic-hot-water [connection dhw-id state & {:keys [until]}]
  (http-put connection (str "domesticHotWater/" dhw-id "/status")
            :form-params {:Mode (cond (= state :auto) "FollowSchedule"
                                      until "TemporaryOverride"
                                      :else "PermanentOverride")
                          :State (when (not= :auto state) (name state))
                          :UntilTime (when until (str until))}))
