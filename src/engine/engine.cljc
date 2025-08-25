(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   #?(:cljs [systems.dev.leva-rules :as leva-rules])
   [com.rpl.specter :as sp]
   [dofida.dofida :as dofida]
   [engine.esse :as esse]
   [engine.refresh :refer [*refresh?]]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as entities-2d]
   [play-cljc.transforms :as t]
   [systems.dev.dev-only :as dev-only]
   [systems.input :as input]
   [systems.time :as time]
   [systems.window :as window]))

(defn update-window-size! [width height]
  (swap! world/world* window/set-window width height))

(defn update-mouse-coords! [x y]
  (swap! world/world* input/insert-mouse x y))

(defn compile-shader [game world*]
  (doseq [{:keys [esse-id compile-fn]} (o/query-all @world* ::dofida/compile-shader)]
    (swap! world* #(o/insert % esse-id ::esse/compiling-shader true))
    (let [compiled-shader (compile-fn game)]
      (swap! world* #(-> %
                         (o/retract esse-id ::esse/compiling-shader)
                         (o/insert esse-id ::esse/compiled-shader compiled-shader)
                         (o/fire-rules))))))

(defn load-image [game world*]
  (doseq [{:keys [esse-id image-path]} (o/query-all @world* ::dofida/load-image)]
    (swap! world* #(o/insert % esse-id ::esse/loading-image true))
    (println "loading image" esse-id image-path)
    (utils/get-image
     image-path
     (fn [{:keys [data width height]}]
       (let [image-entity (entities-2d/->image-entity game data width height)
             image-entity (c/compile game image-entity)
             loaded-image (assoc image-entity :width width :height height)]
         (swap! world*
                #(-> %
                     (o/retract esse-id ::esse/loading-image)
                     (o/insert esse-id ::esse/current-sprite loaded-image)
                     (o/fire-rules))))))))

(defn compile-all [game world*]
  (compile-shader game world*)
  (load-image game world*))

(def all-systems
  [window/system
   input/system
   dofida/system
   dev-only/system
   #?(:cljs leva-rules/system)])

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
  (let [[game-width game-height] (utils/get-size game)
        all-rules (apply concat (sp/select [sp/ALL ::world/rules] all-systems))
        all-init  (sp/select [sp/ALL ::world/init some?] all-systems)]
    (swap! world/world*
           (fn [world]
             (-> (world/init-world world all-rules)
                 (as-> w (reduce (fn [w init-fn] (init-fn w)) w all-init))
                 (window/set-window game-width game-height)
                 (o/fire-rules))))
    (compile-all game world/world*)))

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear {:color [(/ 242 255) (/ 242 255) (/ 248 255) 1] :depth 1}})

(defn make-limited-logger [limit]
  (let [counter (atom 0)]
    (fn [& args]
      (when (< @counter limit)
        (apply #?(:clj println :cljs js/console.error) args)
        (swap! counter inc)))))

(def log-once (make-limited-logger 24))

(defn tick [game]
  (if @*refresh?
    (try (println "calling (init game)")
         (swap! *refresh? not)
         (init game)
         (catch #?(:clj Exception :cljs js/Error) err
           (log-once "init-error" err)))
    (try
      (let [{:keys [delta-time total-time]} game
            world (swap! world/world*
                         #(-> %
                              (time/insert total-time delta-time)
                              o/fire-rules))
            {game-width :width game-height :height} (first (o/query-all world ::window/window))
            shader-esses (o/query-all world ::dofida/shader-esse)
            sprite-esses (o/query-all world ::dofida/sprite-esse)]
        (when (and (pos? game-width) (pos? game-height))
          (c/render game (-> screen-entity
                             (update :viewport assoc :width game-width :height game-height)))
          (doseq [shader-esse shader-esses]
            (c/render game (-> (:compiled-shader shader-esse)
                               (t/project 1 1) ;; still not sure why this work
                               (t/translate 0 0.1)
                               (t/scale 1 0.8))))
          (doseq [sprite-esse sprite-esses]
            (let [{:keys [x y current-sprite]} sprite-esse]
              (c/render game
                        (-> current-sprite
                            (t/project game-width game-height)
                            (t/translate x y)
                            (t/scale (:width current-sprite)
                                     (:height current-sprite))))))))
      (catch #?(:clj Exception :cljs js/Error) err
        (log-once "tick-error" err))))
  game)


(comment
  (o/query-all @world/world* ::input/mouse))