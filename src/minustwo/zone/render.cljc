(ns minustwo.zone.render
  (:require
   #?(:clj  [minusthree.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minusthree.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.game :as game]
   [engine.utils :as utils]
   [engine.world :as world]
   [minusthree.gl.constants :refer [GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA]]
   [minustwo.systems.time :as time]
   [minustwo.systems.view.room :as room]
   [odoyle.rules :as o]))

(defn render [world game]
  (let [{:keys [ctx window]}   (utils/query-one world ::room/data)
        {:keys [width height]} window]
    (when ctx
      (gl ctx blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
      (gl ctx clearColor 0.02 0.02 0.12 1.0)
      (gl ctx clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
      (gl ctx viewport 0 0 width height)
      (doseq [render-fn @(::game/render-fns* game)]
        (render-fn world game)))))

(defn render-zone [game]
  (let [total-time (:total-time game)
        delta-time (:delta-time game)
        world      (swap! (::world/atom* game)
                          (fn [world] (-> world (time/insert total-time delta-time) (o/fire-rules))))]
    (render world game)))
