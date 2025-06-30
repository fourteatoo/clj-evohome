(ns fourteatoo.clj-evohome.api-test
  (:require [fourteatoo.clj-evohome.api :as api]
            [fourteatoo.clj-evohome.mock-http :as mock]
            [clojure.test :refer :all]
            [java-time :as jt]))

(def dummy-client
  (api/->EvoClient "john@smith.address"
                   "password"
                   (atom {:access-token "access token",
                          :token-type "bearer",
                          :expires-in (* 60 60),
                          :refresh-token "refresh token",
                          :scope "EMEA-V1-Basic EMEA-V1-Anonymous",
                          :expires (jt/plus (jt/local-date-time)
                                            (jt/hours 1))})))

(comment
  (type dummy-client)
  (mock/call-with-mocks (fn []
                          (api/user-account-info dummy-client))))

(use-fixtures :once mock/call-with-mocks)

(defn check-auth-token [response]
  (let [tokens @(:tokens dummy-client)]
    (is (= (str (:token-type tokens) " " (:access-token tokens))
           (get-in response [:opts :headers :authorization])))))

(defn assert-empty-body [response]
  (is (nil? (get-in response [:opts :body]))))

(defn check-body [expected response]
  (is (= expected (get-in response [:opts :body]))))

(deftest user-account-info
  (let [response (api/user-account-info dummy-client)]
    (is (= "https://tccna.honeywell.com/WebAPI/emea/api/v1/userAccount"
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest installations-by-user
  (let [user (rand-int 1000)
        response (api/installations-by-user dummy-client user)]
    (is (= (str "https://tccna.honeywell.com/WebAPI/emea/api/v1/location/installationInfo")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest installation-at-location
  (let [location (rand-int 1000)
        response (api/installation-at-location dummy-client location)]
    (is (= (str "https://tccna.honeywell.com/WebAPI/emea/api/v1/location/" location "/installationInfo")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest get-system-status
  (let [system (rand-int 1000)
        response (api/get-system-status dummy-client system)]
    (is (= (str "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureControlSystem/" system "/status")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest set-system-mode
  (let [system (rand-int 1000)
        mode (rand-nth ["foo" "bar"])
        response (api/set-system-mode dummy-client system mode)]
    (is (= (str "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureControlSystem/" system "/mode")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest set-zone-temperature
  (let [zone (rand-int 1000)
        temp (rand-int 30)
        response (api/set-zone-temperature dummy-client zone temp)]
    (is (= (str "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/" zone "/heatSetPoint")
           (:url response)))
    (check-auth-token response)
    (check-body (str "{\"setpointMode\":\"PermanentOverride\",\"heatSetpointValue\":" temp ",\"timeUntil\":null}")
                response)))

(deftest cancel-zone-override
  (let [zone (rand-int 1000)
        response (api/cancel-zone-override dummy-client zone)]
    (is (= (str "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/" zone "/heatSetPoint")
           (:url response)))
    (check-auth-token response)
    (check-body "{\"setpointMode\":\"FollowSchedule\"}"
                response)))

(deftest get-zone-schedule
  (let [zone (rand-int 1000)
        response (api/get-zone-schedule dummy-client zone)]
    (is (= (str "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/" zone "/schedule")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest set-zone-schedule
  (let [response (api/set-zone-schedule dummy-client 1 {:foo 1 :bar 2})]
    (is (= "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/1/schedule"
           (:url response)))
    (check-auth-token response)
    (check-body "{\"foo\":1,\"bar\":2}" response)))

(deftest get-location-status
  (let [location (rand-int 1000)
        response (api/get-location-status dummy-client location)]
    (is (= (str "https://tccna.honeywell.com/WebAPI/emea/api/v1/location/" location "/status")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest get-domestic-hot-water
  (let [dhw (rand-int 1000)
        response (api/get-domestic-hot-water dummy-client dhw)]
    (is (= (str "https://tccna.honeywell.com/WebAPI/emea/api/v1/domesticHotWater/" dhw "/status")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

(deftest set-domestic-hot-water
  (let [dhw (rand-int 1000)
        state (rand-nth [:on :off :dunno])
        response (api/set-domestic-hot-water dummy-client dhw state)]
    (is (= (str "https://tccna.honeywell.com/WebAPI/emea/api/v1/domesticHotWater/" dhw "/status")
           (:url response)))
    (check-auth-token response)
    (assert-empty-body response)))

