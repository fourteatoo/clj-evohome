(ns fourteatoo.clj-evohome.cached
  (:require [fourteatoo.clj-evohome.api :as api]
            [camel-snake-kebab.core :as csk]
            [clojure.core.memoize :as memo]
            [java-time :as jt]))

(def get-user-account
  (memo/ttl #(api/get-user-account %)
            :ttl/threshold (* 60 60 1000)))

(def get-installations
  (memo/ttl #(api/get-installations %)
            :ttl/threshold (* 3 60 1000)))

(defn- list-system-modes
  "Enumerate for each system in `installations` the supported modes.
  The collection `installations` should be like the one returned by
  the `api/get-installations` Return a map from system ID to a set
  of modes."
  [installations]
  (->> installations
       (mapcat (fn [inst]
                 (->> (:gateways inst)
                      (mapcat :temperature-control-systems)
                      (map (fn [sys]
                             [(:system-id sys)
                              (set (map (comp csk/->kebab-case-keyword :system-mode)
                                        (:allowed-system-modes sys)))])))))
       (into {})))

(defn- index-zones [installations]
  (reduce (fn [m installation]
            (assoc m (get-in installation [:location-info :name])
                   (->> (:gateways installation)
                        (mapcat (fn [gateway]
                                  (mapcat (fn [tcs]
                                            (map (juxt :name identity)
                                                 (:zones tcs)))
                                          (:temperature-control-systems gateway))))
                        (into {}))))
          {} installations))

(defn- index-locations [installations]
  (reduce (fn [m installation]
            (assoc m (get-in installation [:location-info :name])
                   (get-in installation [:location-info :location-id])))
          {} installations))

(defn- get-metadata [c]
  (let [user (get-user-account c)
        installations (get-installations c (:user-id user) :tcs true)
        system-modes (list-system-modes installations)
        locations (index-locations installations)
        zones (index-zones installations)]
    {:user user
     ;; :installations installations
     :system-modes system-modes
     :locations locations
     :zones zones}))

(def metadata
  (memo/ttl get-metadata :ttl/threshold (* 5 60 1000)))

(defn- location-name [location]
  (get-in location [:location-info :name]))

(defn- location-id [location]
  (get-in location [:location-info :location-id]))

(defn path->zone-id [c path]
  (:zone-id (get-in (:zones (metadata c)) path)))

(defn- ->zone-id [c zone-or-path]
  (if (vector? zone-or-path)
    (path->zone-id c zone-or-path)
    zone-or-path))

(defn set-temperature [c zone-or-path temperature & {:keys [until]}]
  (api/set-zone-temperature c (->zone-id c zone-or-path) temperature :until until))

(defn cancel-override [c zone-or-path]
  (api/cancel-zone-override c (->zone-id zone-or-path)))

(defn- location-modes [c location-id]
  (get-in (metadata c) [:system-modes location-id]))

(defn- ->location-id [name-or-id]
  (or (get (metadata c) :locations)
      name-or-id))

(defn- assert-mode-allowed [c name-or-location-id mode]
  (let [modes (location-modes c (->location-id name-or-location-id))]
    (when-not (contains? modes mode)
      (throw (ex-info "unknown mode for the installation"
                      {:location name-or-location-id
                       :mode mode
                       :allowed modes})))))

(defn set-mode [c name-or-location-id mode]
  (assert-mode-allowed c location mode)
  (->> (select-locations c location-id)
       (mapcat :gateways)
       (mapcat :temperature-control-systems)
       (map :system-id)
       (run! #(api/set-system-mode c % mode))))
