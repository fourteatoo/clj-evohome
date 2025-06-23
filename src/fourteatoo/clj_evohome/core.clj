(ns fourteatoo.clj-evohome.core
  (:require [fourteatoo.clj-evohome.api :as api]
            [camel-snake-kebab.core :as csk]))


(defn select-locations
  "Find by name the locations matching `location-name`."
  [c location-name]
  (->> (api/user-account-info c)
       :user-id
       (api/installations-by-user c)
       (filter #(= location-name (get-in % [:location-info :name])))))

(defn select-zones
  "Find by name the zones matching `location` and `zone`."
  [c location zone]
  (->> (select-locations c location)
       (mapcat :gateways)
       (mapcat :temperature-control-systems)
       (mapcat :zones)
       (filter #(= zone (:name %)))))

(defn set-temperature [c location zone temperature & {:keys [until]}]
  (->> (select-zones c location zone)
       (map :zone-id)
       (run! #(api/set-zone-temperature c % temperature :until until))))

(defn cancel-override [c location zone]
  (->> (select-zones c location zone)
       (map :zone-id)
       (run! (partial api/cancel-zone-override c))))

(defn set-mode [c location mode]
  (->> (select-locations c location)
       (mapcat :gateways)
       (mapcat :temperature-control-systems)
       (map :system-id)
       (run! #(api/set-system-mode c % mode))))

(defn list-system-modes
  "Enumerate for each system in `installations` the supported modes.
  The collection `installations` should be like the one returned by
  the `installations-by-user` Return a map from system ID to a vector
  of modes."
  [installations]
  (->> installations
       (mapcat (fn [inst]
                 (->> (:gateways inst)
                      (mapcat :temperature-control-systems)
                      (map (fn [sys]
                             [(:system-id sys)
                              (mapv (comp csk/->kebab-case-keyword :system-mode)
                                    (:allowed-system-modes sys))])))))
       (reduce (fn [m [k v]]
                 (assoc m k v))
               {})))
