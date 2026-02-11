(ns minusthree.engine.engine
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.loading :as loading]
   #?(:clj [minusthree.engine.rendering :as rendering])
   [minusthree.engine.systems :as systems]
   [minusthree.engine.time :as time]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::init-game (s/keys :req [::world/this ::time/total]))

(defn init [game]
  (->> (world/init-world game systems/all)
       (loading/init-channel)
       #?(:clj (rendering/init))
       (s/assert ::init-game)))

(defn tick [game]
  (-> game
      (update ::world/this o/fire-rules)
      (loading/loading-zone)
      #?(:clj (rendering/rendering-zone))))

(defn destroy [game]
  (-> game
      #?(:clj (rendering/destroy))))
