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

(defrecord EvoClient [username password tokens])

(defn authenticate-client [username password]
  (let [tokens (basic-login username password)]
    (->EvoClient username password (atom tokens))))

(defn- make-http-headers [client]
  (let [{:keys [access-token token-type]} (auth-tokens client)]
    {:accept "application/json"
     :authorization (str token-type " " access-token)}))

(defn- wrap-http [op]
  (fn [client path & {:as opts}]
    {:pre [(instance? EvoClient client)]}
    (:json (op (str api-url "/" path)
               (http/merge-http-opts {:headers (make-http-headers client)}
                                     opts)))))

(def ^:private http-get (wrap-http #'http/http-get))
(def ^:private http-post (wrap-http #'http/http-post))
(def ^:private http-put (wrap-http #'http/http-put))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn user-account-info
  "Query the user account basic data, such as name, surname, address,
  language, user ID, etc.  Return a map."
  [client]
  (http-get client "userAccount"))

(defn installations-by-user
  "Given a user-id (see `user-account-info`), query each physical
  installation belonging to that user.  The data returned for each
  installation are name, type, adrress, gateways, etc.  Return a
  sequence of maps."
  [client user-id]
  (http-get client "location/installationInfo"
            :query-params {:includeTemperatureControlSystems true
                           :userId user-id}))

(defn installation-at-location
  "Given a location, query the physical installation belonging to that
  location.  The data returned are name, type, adrress, gateways, etc.
  Return a map.  See also `installations-by-user`"
  [client location]
  (http-get client (str "location/" location "/installationInfo")
            :query-params {:includeTemperatureControlSystems true}))

(defn get-system-status
  "Query a specific temperature control system.  A system is associated
  to a gateway; they may be the same physical thing.  Return a map."
  [client system-id]
  (http-get client (str "temperatureControlSystem/" system-id "/status")))

(defn set-system-mode
  "Set the specific system to `mode`.  See also `list-system-modes`."
  [client system-id mode & {:keys [until]}]
  (http-put client (str "temperatureControlSystem/" system-id "/mode")
            :form-params {:SystemMode (csk/->camelCaseString mode)
                          :TimeUntil (when until (str until))
                          :Permanent (nil? until)}))

(defn- zone-heat-set-point [client zone-type zone-id data]
  (http-put client (str zone-type "/" zone-id "/heatSetPoint")
            :content-type "application/json"
            :body data))

(defn set-zone-temperature [client zone-id temperature & {:keys [until]}]
  (zone-heat-set-point client "temperatureZone" zone-id
                       {:SetpointMode (if until
                                        "TemporaryOverride"
                                        "PermanentOverride")
                        :HeatSetpointValue temperature
                        :TimeUntil (when until (str until))}))

(defn cancel-zone-override [client zone-id]
  (zone-heat-set-point client "temperatureZone" zone-id
                       {:SetpointMode "FollowSchedule"}))

(defn get-zone-schedule
  "Return the daily schedule of zone with ID `zone-id`"
  [client zone-id]
  (http-get client (str "temperatureZone/" zone-id "/schedule")))

(defn set-zone-schedule
  "Set the specified `zone-id` to have the daily plan `schedule`. See
  `get-zone-schedule`."
  [client zone-id schedule]
  (http-put client (str "temperatureZone/" zone-id "/schedule")
            :content-type "application/json"
            :body schedule))

(defn get-location-status
  "Get the status of all zones in the specified location `location-id`."
  [client location-id]
  (http-get client (str "location/" location-id "/status")
            :query-params {:includeTemperatureControlSystems true}))

(defn get-domestic-hot-water [client dhw-id]
  (http-get client (str "domesticHotWater/" dhw-id "/status")))

(defn set-domsetic-hot-water [client dhw-id state & {:keys [until]}]
  (http-put client (str "domesticHotWater/" dhw-id "/status")
            :form-params {:Mode (cond (= state :auto) "FollowSchedule"
                                      until "TemporaryOverride"
                                      :else "PermanentOverride")
                          :State (when (not= :auto state) (name state))
                          :UntilTime (when until (str until))}))
