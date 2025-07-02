(ns fourteatoo.clj-evohome.core-test
  (:require
   [clojure.test :refer :all]
   [fourteatoo.clj-evohome.core :as core]
   [fourteatoo.clj-evohome.api :as api]
   [fourteatoo.clj-evohome.mock-http :as mock]
   [java-time :as jt]))

(def bedroom
  {:zone-id "2222222",
   :model-type "HeatingZone",
   :setpoint-capabilities
   {:max-heat-setpoint 35.0,
    :min-heat-setpoint 5.0,
    :value-resolution 0.5,
    :can-control-heat true,
    :can-control-cool false,
    :allowed-setpoint-modes
    ["PermanentOverride" "FollowSchedule" "TemporaryOverride"],
    :max-duration "1.00:00:00",
    :timing-resolution "00:10:00"},
   :schedule-capabilities
   {:max-switchpoints-per-day 6,
    :min-switchpoints-per-day 1,
    :timing-resolution "00:10:00",
    :setpoint-value-resolution 0.5},
   :name "Bedroom",
   :zone-type "RadiatorZone"})

(def kitchen
  {:zone-id "3333333",
   :model-type "HeatingZone",
   :setpoint-capabilities
   {:max-heat-setpoint 35.0,
    :min-heat-setpoint 5.0,
    :value-resolution 0.5,
    :can-control-heat true,
    :can-control-cool false,
    :allowed-setpoint-modes
    ["PermanentOverride" "FollowSchedule" "TemporaryOverride"],
    :max-duration "1.00:00:00",
    :timing-resolution "00:10:00"},
   :schedule-capabilities
   {:max-switchpoints-per-day 6,
    :min-switchpoints-per-day 1,
    :timing-resolution "00:10:00",
    :setpoint-value-resolution 0.5},
   :name "Kitchen",
   :zone-type "RadiatorZone"})

(def bathroom
  {:zone-id "4444444",
   :model-type "HeatingZone",
   :setpoint-capabilities
   {:max-heat-setpoint 35.0,
    :min-heat-setpoint 5.0,
    :value-resolution 0.5,
    :can-control-heat true,
    :can-control-cool false,
    :allowed-setpoint-modes
    ["PermanentOverride" "FollowSchedule" "TemporaryOverride"],
    :max-duration "1.00:00:00",
    :timing-resolution "00:10:00"},
   :schedule-capabilities
   {:max-switchpoints-per-day 6,
    :min-switchpoints-per-day 1,
    :timing-resolution "00:10:00",
    :setpoint-value-resolution 0.5},
   :name "Bathroom",
   :zone-type "RadiatorZone"})

(def living-room
  {:zone-id "5555555",
   :model-type "HeatingZone",
   :setpoint-capabilities
   {:max-heat-setpoint 35.0,
    :min-heat-setpoint 5.0,
    :value-resolution 0.5,
    :can-control-heat true,
    :can-control-cool false,
    :allowed-setpoint-modes
    ["PermanentOverride" "FollowSchedule" "TemporaryOverride"],
    :max-duration "1.00:00:00",
    :timing-resolution "00:10:00"},
   :schedule-capabilities
   {:max-switchpoints-per-day 6,
    :min-switchpoints-per-day 1,
    :timing-resolution "00:10:00",
    :setpoint-value-resolution 0.5},
   :name "Living room",
   :zone-type "RadiatorZone"})

(def system-modes
  [{:system-mode "Auto",
    :can-be-permanent true,
    :can-be-temporary false}
   {:system-mode "AutoWithEco",
    :can-be-permanent true,
    :can-be-temporary true,
    :max-duration "1.00:00:00",
    :timing-resolution "01:00:00",
    :timing-mode "Duration"}
   {:system-mode "AutoWithReset",
    :can-be-permanent true,
    :can-be-temporary false}
   {:system-mode "Away",
    :can-be-permanent true,
    :can-be-temporary true,
    :max-duration "99.00:00:00",
    :timing-resolution "1.00:00:00",
    :timing-mode "Period"}
   {:system-mode "DayOff",
    :can-be-permanent true,
    :can-be-temporary true,
    :max-duration "99.00:00:00",
    :timing-resolution "1.00:00:00",
    :timing-mode "Period"}
   {:system-mode "HeatingOff",
    :can-be-permanent true,
    :can-be-temporary false}
   {:system-mode "Custom",
    :can-be-permanent true,
    :can-be-temporary true,
    :max-duration "99.00:00:00",
    :timing-resolution "1.00:00:00",
    :timing-mode "Period"}])

(def dummy-location
  {:location-info
     {:location-owner
      {:user-id "0000000",
       :username "john@smith.address",
       :firstname "John",
       :lastname "Smith"},
      :location-type "Residential",
      :use-daylight-save-switching true,
      :name "Home",
      :city "Kathmandu",
      :postcode "12543",
      :location-id "1234567",
      :street-address "Gandhi Plaza 12",
      :country "Nepal",
      :time-zone
      {:time-zone-id "WEuropeStandardTime",
       :display-name
       "(UTC+01:00) Amsterdam, Berlin, Bern, Rome, Stockholm, Vienna",
       :offset-minutes 60,
       :current-offset-minutes 120,
       :supports-daylight-saving true}},
     :gateways
     [{:gateway-info
       {:gateway-id "7654321",
        :mac "C025CB82D2CA",
        :crc "AB81",
        :is-wi-fi false},
       :temperature-control-systems
       [{:system-id "1111111",
         :model-type "EvoTouch",
         :zones
         [bedroom
          kitchen
          living-room],
         :allowed-system-modes
         system-modes}]}]})

(defn dummy-installation []
  (core/->IndexedInstallation
   [dummy-location]
   (#'core/index-installation [dummy-location])))

(def dummy-client
  (-> (api/->ApiClient "john@smith.address"
                       "password"
                       (atom {:access-token "access token",
                              :token-type "bearer",
                              :expires-in (* 60 60),
                              :refresh-token "refresh token",
                              :scope "EMEA-V1-Basic EMEA-V1-Anonymous",
                              :expires (jt/plus (jt/local-date-time)
                                                (jt/hours 1))}))
      (assoc ::core/user-id "0000000",
             ::core/installation dummy-installation)))

(use-fixtures :once mock/call-with-mocks)

(defn check-auth-token [response]
  (let [tokens @(:tokens dummy-client)]
    (is (= (str (:token-type tokens) " " (:access-token tokens))
           (get-in response [:opts :headers :authorization])))))

(defn assert-empty-body [response]
  (is (nil? (get-in response [:opts :body]))))

(defn check-body [expected response]
  (is (= expected (get-in response [:opts :body]))))

(deftest cached
  (let [v (atom 0)
        cfn (#'core/cached #(swap! v inc) 2000)]
    (is (= 1 (cfn)))
    (is (= 1 (cfn)))
    (Thread/sleep 300)
    (is (= 1 (cfn)))
    (Thread/sleep 1800)
    (is (= 2 (cfn)))
    (Thread/sleep 300)
    (is (= 2 (cfn)))
    (is (= 2 (cfn)))))

(deftest get-installation
  (is (= (:locations (dummy-installation))
         (core/get-installation dummy-client))))

(deftest get-location
  (is (= dummy-location
         (core/get-location dummy-client ["Home"])))
  (is (= dummy-location
         (core/get-location dummy-client "1234567")))
  (is (thrown? Exception
               (core/get-location dummy-client "foobar")))
  (is (thrown? Exception
               (core/get-location dummy-client ["foobar"]))))

(comment
  (mock/call-with-mocks #(core/set-location-mode dummy-client ["Home"] :away)))

(deftest set-location-mode
  (is (nil?
       (core/set-location-mode dummy-client ["Home"] :away)))
  (is (nil?
       (core/set-location-mode dummy-client "1234567" :day-off)))
  (is (thrown? Exception
               (core/set-location-mode dummy-client "1234567" :foo-bar)))
  (is (thrown? Exception
               (core/set-location-mode dummy-client "foobar" :away)))
  (is (thrown? Exception
               (core/set-location-mode dummy-client ["foobar"] :away))))

(deftest get-location-systems-status
  (is (= [{:opts
	    {:body nil,
	     :headers
	     {:accept "application/json", :authorization "bearer access token"}},
	    :url
	    "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureControlSystem/1111111/status"}]
         (core/get-location-systems-status dummy-client ["Home"])))
  (is (= [{:opts
	    {:body nil,
	     :headers
	     {:accept "application/json", :authorization "bearer access token"}},
	    :url
	    "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureControlSystem/1111111/status"}]
         (core/get-location-systems-status dummy-client "1234567")))
  (is (thrown? Exception
               (core/get-location-systems-status dummy-client "foobar"))))

(deftest set-zone-temperature
  (is (= {:opts
	  {:body
	   "{\"setpointMode\":\"PermanentOverride\",\"heatSetpointValue\":12.3,\"timeUntil\":null}",
	   :content-type "application/json",
	   :headers
	   {:accept "application/json", :authorization "bearer access token"}},
	  :url
	  "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/3333333/heatSetPoint"}
         (core/set-zone-temperature dummy-client ["Home" "Kitchen"] 12.3)))
  (is (= {:opts
	  {:body
	   "{\"setpointMode\":\"PermanentOverride\",\"heatSetpointValue\":23.4,\"timeUntil\":null}",
	   :content-type "application/json",
	   :headers
	   {:accept "application/json", :authorization "bearer access token"}},
	  :url
	  "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/5555555/heatSetPoint"}
         (core/set-zone-temperature dummy-client ["Home" "Living room"] 23.4)))
  (is (= {:opts
	  {:body
	   "{\"setpointMode\":\"PermanentOverride\",\"heatSetpointValue\":23.4,\"timeUntil\":null}",
	   :content-type "application/json",
	   :headers
	   {:accept "application/json", :authorization "bearer access token"}},
	  :url
	  "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/5555555/heatSetPoint"}
         (core/set-zone-temperature dummy-client "5555555" 23.4))))

(deftest cancel-zone-override
  (is (= {:url
	  "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/3333333/heatSetPoint",
	  :opts
	  {:headers
	   {:accept "application/json", :authorization "bearer access token"},
	   :content-type "application/json",
	   :body
	   "{\"setpointMode\":\"FollowSchedule\",\"heatSetpointValue\":0,\"timeUntil\":null}"}}
         (core/cancel-zone-override dummy-client ["Home" "Kitchen"]))))

(deftest get-zone-schedule
  (is (= {:url	  
	   "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/2222222/schedule",
	   :opts
	   {:headers
	    {:accept "application/json", :authorization "bearer access token"},
	    :body nil}}
         (core/get-zone-schedule dummy-client ["Home" "Bedroom"])))
  (is (= {:url
	  "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/2222222/schedule",
	  :opts
	  {:headers
	   {:accept "application/json", :authorization "bearer access token"},
	   :body nil}}
         (core/get-zone-schedule dummy-client "2222222")))
  (is (= {:url
	  "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/foobar/schedule",
	  :opts
	  {:headers
	   {:accept "application/json", :authorization "bearer access token"},
	   :body nil}}
         (core/get-zone-schedule dummy-client "foobar")))
  (is (thrown? Exception
               (core/get-zone-schedule dummy-client ["Shop" "Toilet"]))))

(deftest set-zone-schedule
  (is (= {:url
	  "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/3333333/schedule",
	  :opts
	  {:headers
	   {:accept "application/json", :authorization "bearer access token"},
           :content-type "application/json",
	   :body "{\"foo\":1,\"bar\":2}"}}
         (core/set-zone-schedule dummy-client ["Home" "Kitchen"] {:foo 1 :bar 2})))
  (is (= {:url
	  "https://tccna.honeywell.com/WebAPI/emea/api/v1/temperatureZone/3333333/schedule",
	  :opts
	  {:headers
	   {:accept "application/json", :authorization "bearer access token"},
           :content-type "application/json",
	   :body "{\"foo\":2,\"bar\":3}"}}
         (core/set-zone-schedule dummy-client "3333333" {:foo 2 :bar 3})))
  (is (thrown? Exception
               (core/set-zone-schedule dummy-client ["Shop" "Kitchen"] {:foo 1 :bar 2})))
  (is (thrown? Exception
               (core/set-zone-schedule dummy-client ["Home" "Guest room"] {:foo 1 :bar 2}))))

(deftest get-location-status
  (is (= {:url	  
	  "https://tccna.honeywell.com/WebAPI/emea/api/v1/location/1234567/status",
	  :opts
	  {:headers
	   {:accept "application/json", :authorization "bearer access token"},
	   :query-params {:include-temperature-control-systems false},
	   :body nil}}
         (core/get-location-status dummy-client ["Home"])))
  (is (= {:url	  
	  "https://tccna.honeywell.com/WebAPI/emea/api/v1/location/1234567/status",
	  :opts
	  {:headers
	   {:accept "application/json", :authorization "bearer access token"},
	   :query-params {:include-temperature-control-systems false},
	   :body nil}}
         (core/get-location-status dummy-client "1234567")))
  (is (thrown? Exception
               (core/get-location-status dummy-client "foobar" :tcs? true))))
