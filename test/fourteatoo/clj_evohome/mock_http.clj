(ns fourteatoo.clj-evohome.mock-http
  (:require [fourteatoo.clj-evohome.http :as http]
            [cheshire.core :as json]))

(defn mock-http [url & [opts]]
  (let [opts (dissoc opts :cookie-store)]
    {:status 200
     :headers {:content-type "json"}
     :body (json/generate-string {:url url :opts opts})}))

(defn call-with-mocks [f]
  (with-redefs [clj-http.client/get mock-http
                clj-http.client/put mock-http
                clj-http.client/post mock-http]
    (f)))
