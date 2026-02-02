(ns minusthree.engine.engine
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.systems :as systems]
   [minusthree.engine.time :as time]
   [minusthree.engine.world :as world]
   [minusthree.engine.loading :as loading]
   [odoyle.rules :as o]))

(s/def ::init-game (s/keys :req [::world/this ::time/total]))

(defn init [game]
  (->> (world/init-world game systems/all)
       (loading/init-channel)
       ((fn [g] (update g ::world/this
                        loading/insert-load-fn
                        ::dummy (fn [] (println "this is what gets loaded") 
                                  []))))
       (s/assert ::init-game)))

(declare rendering-zone)

(defn tick [game]
  (-> game
      (update ::world/this o/fire-rules)
      (loading/loading-zone)
      (rendering-zone)))

(defn rendering-zone [game]
  ;; (println "render" (::time/total game))
  game)
