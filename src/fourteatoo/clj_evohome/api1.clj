(ns fourteatoo.clj-evohome.api1
  (:require [clojure.string :as s]
            [clj-evohome.http :refer :all]
            [cheshire.core :as json]
            [java-time :as jt]))


;; from a casual Google search
(def default-application-id "91db1612-73fd-4500-91b2-e63b069b185c")

(def ^:dynamic base-url
  "https://mytotalconnectcomfort.com/WebAPI/api")

(defn- wrap-http [connection op url & [opts]]
  (-> (op (str base-url "/" url)
          (merge-http-opts
           {:headers (if connection
                       {:SessionId (get-in connection [:session :sessionId])}
                       {})
            :content-type "application/json"
            :accept "application/json"
            :cookie-store (:cookie-store connection)}
           opts))
      :json))

(defn connect [username password & [application-id]]
  (let [cookie-store (make-cookie-store)]
    {:cookie-store cookie-store
     :session (wrap-http nil http-post "Session"
                         {:body (json/generate-string
                                 {:Username username
                                  :Password password
                                  :ApplicationId (or application-id
                                                     default-application-id)})
                          :cookie-store cookie-store})}))

(defn get-locations [connection]
  (wrap-http connection http-get "locations"
             {:query-params {"userId" (get-in connection [:session :userInfo :userID])
                             "allData" true}}))

(defn get-task-status [connection task-id]
  (wrap-http connection http-get "commTasks"
             {:query-params {"commTaskId" task-id}}))

(defn set-quick-action [connection location action until]
  (wrap-http connection http-put "evoTouchSystems"
             {:query-params {"locationId" location}
              :body (json/generate-string
                     {:action action
                      :until until})}))

(defn get-gateways [connection location]
  (wrap-http connection http-get "gateways"
             {:query-params {"locationId" location
                             "allData" false}}))

(defn heat-set-point [connection device status & {:keys [temperature next-time]}]
  (wrap-http connection http-put
             (str "devices/" device
                  "/thermostat/changeableValues/heatSetpoint")
             {:body {:temperature temperature
                     :status status
                     :until next-time}}))

(defn set-status [connection mode & {:keys [until]}]
  (wrap-http connection http-put
             (str "temperatureControlSystem/" (get-in connection [:session :systemId])
                  "/mode")
             {:body {:SystemMode mode
                     :TimeUntil until
                     :Permanent (nil? until)}}))

(comment
  (def c (connect username password))
  (get-locations c)
  (get-gateways c 3433565)
  (set-quick-action c 3433565 "away"
                    (str (jt/plus (jt/local-date-time)
                                  (jt/days 1))))
  (set-status c 3433565 "away"))
