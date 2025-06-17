(ns fourteatoo.clj-evohome.http
  "Various fairly low level HTTP primitives and wrappers of `clj-http`."
  {:no-doc true}
  (:require [clojure.string :as s]
            [clj-http.client :as http]
            clj-http.cookies
            [cheshire.core :as json]
            [camel-snake-kebab.core :as csk]))

(def make-cookie-jar
  clj-http.cookies/cookie-store)

(defonce default-cookie-jar (make-cookie-jar))

(defn- keywordify-map [m]
  (->> (map (juxt (comp keyword s/lower-case key) val) m)
       (into {})))

(defn- get-header [response header]
  (get (keywordify-map (:headers response)) header))

(defn- get-content-type [response]
  (s/split (get-header response :content-type) #";"))

(defn- json-content? [response]
  (boolean (re-find #"json" (first (get-content-type response)))))

(defn- restify [action]
  (fn [url & [opts]]
    (let [add-url #(assoc % :url url)
          add-json (fn [response]
                    (if (json-content? response)
                      (assoc response :json
                             (-> response
                                 :body
                                 (json/parse-string csk/->kebab-case-keyword)))
                      response))]
      (-> (try
            (action (str url)
                    (merge {:cookie-store default-cookie-jar}
                           (update opts :body
                                   (fn [body]
                                     (if (map? body)
                                       (json/generate-string body {:key-fn csk/->camelCaseString})
                                       body)))))
            (catch Exception e
              (throw
               ;; augment exception with context
               (ex-info "HTTP op exception" {:op action :url url :opts opts} e))))
          add-url
          add-json))))

(def http-get (restify #'http/get))
(def http-post (restify #'http/post))
(def http-put (restify #'http/put))

(defn merge-http-opts [opts1 opts2]
  (merge-with (fn [o1 o2]
                (if (map? o1)
                  (merge o1 o2)
                  o2))
              opts1 opts2))
