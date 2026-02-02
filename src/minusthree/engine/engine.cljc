(ns minusthree.engine.engine
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.systems :as systems]
   [minusthree.engine.time :as time]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::init-game (s/keys :req [::world/this ::time/total]))

(defn init [game]
  (->> (world/init-world game systems/all)
       (s/assert ::init-game)))

(declare render)

(defn tick [game]
  (-> game
      (update ::world/this o/fire-rules)
      (render)))

(defn render [game]
;;   (println "render" (::time/total game))
  game)
