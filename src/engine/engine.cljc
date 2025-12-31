(ns engine.engine
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.game :as game :refer [gl-ctx]]
   [engine.refresh :refer [*refresh?]]
   [engine.world :as world]
   [minustwo.gl.constants :refer [GL_BLEND GL_CULL_FACE GL_DEPTH_TEST]]
   [minustwo.systems :as systems]
   [minustwo.zone.loading :refer [loading-zone]]
   [minustwo.zone.render :refer [render-zone]]
   [odoyle.rules :as o]))

(declare refresh-zone progress-zone error-zone)

(def ANSI-RED "\u001B[31m")
(def ANSI_RESET "\u001B[0m")
(defn RED [text] (str ANSI-RED text ANSI_RESET))

(defn init [game]
  (try
    (let [ctx (gl-ctx game)]
      (gl ctx enable GL_BLEND)
      (gl ctx enable GL_CULL_FACE)
      (gl ctx enable GL_DEPTH_TEST))
    (let [{:keys [all-rules before-fns init-fns after-fns render-fns]} (world/build-systems systems/all)]
      (some-> (::game/render-fns* game) (reset! render-fns))
      (swap! (::world/atom* game)
             (fn [world]
               (-> (world/init-world world game all-rules before-fns init-fns after-fns)
                   (o/fire-rules)))))
    (catch  #?(:clj Exception :cljs js/Error) err
      ;; error handling when repl'ing need hammock time
      (println (RED "[init error]") (or (:via (Throwable->map err))
                                        (dissoc (Throwable->map err) :trace)))))
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
