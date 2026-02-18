(ns minustwo.zone.init
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.game :as game :refer [gl-ctx]]
   [engine.world :as world]
   [minustwo.gl.constants :refer [GL_BLEND GL_CULL_FACE GL_DEPTH_TEST
                                  GL_MULTISAMPLE]]
   [minustwo.systems :as systems]
   [odoyle.rules :as o]))

(def ANSI-RED "\u001B[31m")
(def ANSI_RESET "\u001B[0m")
(defn RED [text] (str ANSI-RED text ANSI_RESET))

(defn init-zone [game]
  (try
    (let [ctx (gl-ctx game)]
      (gl ctx enable GL_BLEND)
      (gl ctx enable GL_CULL_FACE)
      #?(:clj (gl ctx enable GL_MULTISAMPLE))
      (gl ctx enable GL_DEPTH_TEST))
    (let [{:keys [all-rules before-fns init-fns after-fns render-fns]} (world/build-systems systems/all)]
      (some-> (::game/render-fns* game) (reset! render-fns))
      (swap! (::world/atom* game)
             (fn [world]
               (-> (world/init-world world game all-rules before-fns init-fns after-fns)
                   (o/fire-rules)))))
    (catch  #?(:clj Exception :cljs js/Error) err
      ;; error handling when repl'ing need hammock time
      (println (RED "[init error]"))
      (throw err)))
  game)
