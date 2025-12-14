(ns engine.engine
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.refresh :refer [*refresh?]]
   [engine.world :as world]
   [minustwo.game :as game]
   [minustwo.gl.constants :refer [GL_BLEND]]
   [minustwo.systems :as systems]
   [minustwo.zone.loading :refer [loading-zone]]
   [minustwo.zone.render :refer [render-zone]]
   [odoyle.rules :as o]))

(declare refresh-zone progress-zone error-zone)

(defn init [game]
  (gl (:webgl-context game) enable GL_BLEND)
  (let [{:keys [all-rules before-fns init-fns after-fns render-fns]} (world/build-systems systems/all)]
    (some-> (::game/render-fns* game) (reset! render-fns))
    (swap! (::world/atom* game)
           (fn [world]
             (-> (world/init-world world game all-rules before-fns init-fns after-fns)
                 (o/fire-rules)))))
  game)

(defn tick [game]
  (try
    (if @*refresh?
      (refresh-zone game)
      (progress-zone game))
    (catch #?(:clj Exception :cljs js/Error) err
      (error-zone game err)))
  game)

(defn refresh-zone [game]
  (reset! *refresh? false)
  (init game))

(defn progress-zone [game]
  (loading-zone game)
  (render-zone game))

(defn error-zone [_game err]
  (throw err))
