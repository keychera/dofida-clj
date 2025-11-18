(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [assets.asset :as asset]
   [com.rpl.specter :as sp]
   [engine.refresh :refer [*refresh?]]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [rules.alive :as alive]
   [rules.dofida :as dofida]
   [rules.firstperson :as firstperson]
   [rules.interface.input :as input]
   [rules.time :as time]
   [rules.window :as window]
   [rules.camera.arcball :as arcball]))

(defn ->game [context]
  (merge
   (c/->game context)
   {::render-fns* (atom nil)}
   (world/->init)))

(def all-systems
  [time/system

   asset/system
   window/system

   input/system
   firstperson/system
   arcball/system
   
   dofida/system
   alive/system])

(defn init [game]
  (println "init game")
  (gl game enable (gl game BLEND))

  (let [all-rules  (apply concat (sp/select [sp/ALL ::world/rules] all-systems))
        all-init   (sp/select [sp/ALL ::world/init-fn some?] all-systems)
        before-fns (sp/select [sp/ALL ::world/before-load-fn some?] all-systems)
        after-fns  (sp/select [sp/ALL ::world/after-load-fn some?] all-systems)
        render-fns (sp/select [sp/ALL ::world/render-fn some?] all-systems)]

    (swap! (::world/init-cnt* game) inc)
    (reset! (::render-fns* game) render-fns)
    (swap! (::world/atom* game)
           (fn [world]
             (-> (world/init-world world game all-rules before-fns after-fns)
                 (as-> w (reduce (fn [w' init-fn] (init-fn w' game)) w all-init))
                 (o/fire-rules))))
    (asset/load-asset (::world/atom* game) game)))

(defn tick [game]
  (if @*refresh?
    (try (println "refresh game")
         (swap! *refresh? not)
         (init game)
         #?(:clj  (catch Exception err (throw err))
            :cljs (catch js/Error err
                    (utils/log-limited err "[init-error]"))))
    (try
      (let [[game-width game-height] (utils/get-size game)
            {:keys [total-time delta-time]} game

            world (swap! (::world/atom* game)
                         #(-> %
                              (window/set-window game-width game-height)
                              (time/insert total-time delta-time)
                              (o/fire-rules)))]
        (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
        (gl game clearColor 0.62 0.62 0.82 1.0)
        (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT)))
        (gl game viewport 0 0 game-width game-height)

        (doseq [render-fn @(::render-fns* game)]
          (render-fn world game)))
      #?(:clj  (catch Exception err (throw err))
         :cljs (catch js/Error err
                 (utils/log-limited err "[tick-error]")))))
  game)

