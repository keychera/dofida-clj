(ns minustwo.zone.render
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.world :as world]
   [minustwo.game :as game]
   [minustwo.gl.constants :refer [GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA]]
   [minustwo.gl.gl-system :as gl-system]
   [odoyle.rules :as o]
   [minustwo.systems.time :as time]
   [minustwo.utils :as utils]))

(defn render-zone [game]
  (let [total-time             (:total-time game)
        delta-time             (:delta-time game)
        world                  (swap! (::world/atom* game)
                                      (fn [world] (-> world (time/insert total-time delta-time) (o/fire-rules))))
        {:keys [ctx window]}   (utils/query-one world ::gl-system/data)
        {:keys [width height]} window]
    (when ctx
      (gl ctx blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
      (gl ctx clearColor 0.02 0.02 0.12 1.0)
      (gl ctx clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
      (gl ctx viewport 0 0 width height)
      (doseq [render-fn @(::game/render-fns* game)]
        (render-fn world game)))))