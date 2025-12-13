(ns minustwo.zone.render
  (:require
   [engine.world :as world]
   [minustwo.game :as game]
   [odoyle.rules :as o]
   [rules.time :as time]))

(defn render-zone [game]
  (let [total-time (:total-time game)
        delta-time (:delta-time game)
        world      (swap! (::world/atom* game)
                          (fn [world] (-> world (time/insert total-time delta-time) (o/fire-rules))))]
    (doseq [render-fn @(::game/render-fns* game)]
      (render-fn world game))))