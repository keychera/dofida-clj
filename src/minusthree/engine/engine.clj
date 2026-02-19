(ns minusthree.engine.engine
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.rendering :as rendering]
   [minusthree.engine.systems :as systems]
   [minusthree.engine.time :as time]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::init-game (s/keys :req [::world/this ::time/total]))

(defn init [game]
  (->> (world/init-world game systems/all)
       (loading/init-channel)
       (rendering/init)
       (s/assert ::init-game)))

(defn tick [{:keys [refresh-flag*] :as game}]
  (let [refresh? (some-> refresh-flag* deref)]
    (when refresh? (reset! refresh-flag* false))
    (cond-> game
      refresh? (world/post-world)
      true     (update ::world/this o/fire-rules)
      true     (loading/loading-zone)
      true     (rendering/rendering-zone))))

(defn destroy [game]
  (-> game
      (rendering/destroy)))
