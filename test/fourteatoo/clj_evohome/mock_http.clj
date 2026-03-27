(ns fourteatoo.clj-evohome.mock-http
  (:require [fourteatoo.clj-evohome.http :as http]
            hato.client
            [cheshire.core :as json]))

(defn mock-http [url & [opts]]
  (let [opts (dissoc opts :cookie-store :http-client)]
    {:status 200
     :headers {:content-type "json"}
     :body (json/generate-string {:url url :opts opts})}))

(defn call-with-mocks [f]
  (with-redefs [hato.client/get mock-http
                hato.client/put mock-http
                hato.client/post mock-http]
    (f)))
