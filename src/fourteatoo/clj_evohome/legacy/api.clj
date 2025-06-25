(ns fourteatoo.clj-evohome.legacy.api
  "Interface to the legacy REST API."
  {:no-doc true}
  (:require [clojure.string :as s]
            [fourteatoo.clj-evohome.http :as http]
            [cheshire.core :as json]
            [java-time :as jt]))


;; from a casual Google search
(def ^:private default-application-id "91db1612-73fd-4500-91b2-e63b069b185c")

(def ^:private base-url
  "https://mytotalconnectcomfort.com/WebAPI/api")

(defn- wrap-http [op]
  (fn [connection path & [opts]]
    (-> (op (str base-url "/" path)
            (http/merge-http-opts
             {:headers (if connection
                         {:SessionId (get-in connection [:session :session-id])}
                         {})
              :content-type "application/json"
              :accept "application/json"
              :cookie-store (:cookie-store connection)}
             opts))
        :json)))

(def ^:private http-get (wrap-http http/http-get))
(def ^:private http-post (wrap-http http/http-post))
(def ^:private http-put (wrap-http http/http-put))

(defrecord EvoSession [cookie-jar session])

(defn connect [username password & [application-id]]
  (let [cookie-jar (http/make-cookie-jar)]
    (->EvoSession cookie-jar
                  (http-post nil "Session"
                             {:body {:Username username
                                     :Password password
                                     :ApplicationId (or application-id
                                                        default-application-id)}
                              :cookie-store cookie-jar}))))

(defn get-locations [connection]
  (http-get connection "locations"
            {:query-params {"userId" (get-in connection [:session :user-info :user-id])
                            "allData" true}}))

(defn get-task-status [connection task-id]
  (http-get connection "commTasks"
            {:query-params {"commTaskId" task-id}}))

(defn set-quick-action [connection location action until]
  (http-put connection "evoTouchSystems"
            {:query-params {"locationId" location}
             :body {:action action
                    :until until}}))

(defn get-gateways [connection location]
  (http-get connection "gateways"
            {:query-params {"locationId" location
                            "allData" false}}))

(defn heat-set-point [connection device status & {:keys [temperature next-time]}]
  (http-put connection
            (str "devices/" device
                 "/thermostat/changeableValues/heatSetpoint")
            {:body {:temperature temperature
                    :status status
                    :until next-time}}))

(defn set-status [connection system mode & {:keys [until]}]
  (http-put connection
            (str "temperatureControlSystem/" system #_(get-in connection [:session :session-id])
                 "/mode")
            {:body {:SystemMode mode
                    :TimeUntil until
                    :Permanent (nil? until)}}))
