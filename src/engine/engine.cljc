(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.esse :as esse]
   [engine.refresh :refer [*refresh?]]
   [engine.world :as world]
   [engine.utils :as utils]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as entities-2d]
   [play-cljc.transforms :as t]))

(defn update-window-size! [width height]
  (swap! world/world* o/insert ::world/window {::world/width width ::world/height height}))

(defn update-mouse-coords! [x y]
  (swap! world/world* o/insert ::world/mouse {::world/x x ::world/y y}))

(defn compile-shader [game world*]
  (doseq [{:keys [esse-id compile-fn]} (o/query-all @world* ::world/compile-shader)]
    (swap! world* #(o/insert % esse-id ::esse/compiling-shader true))
    (let [compiled-shader (compile-fn game)]
      (swap! world* #(-> %
                           (o/retract esse-id ::esse/compiling-shader)
                           (o/insert esse-id ::esse/compiled-shader compiled-shader)
                           (o/fire-rules))))))

(defn load-image [game world*]
  (doseq [{:keys [esse-id image-path]} (o/query-all @world* ::world/load-image)]
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

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game ONE) (gl game ONE_MINUS_SRC_ALPHA))
  (let [[game-width game-height] (utils/get-size game)]
    (reset! world/world*
            (-> world/dofida-world
                (o/insert ::world/window
                          {::world/width game-width
                           ::world/height game-height})
                (o/fire-rules)))
    (compile-all game world/world*)))

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear {:color [(/ 7 255) (/ 7 255) (/ 22 255) 1] :depth 1}})


(defn tick [game]
  (if @*refresh?
    (try (println "calling (compile-all game)")
         (swap! *refresh? not)
         (init game)
         (compile-all game world/world*)
         (catch #?(:clj Exception :cljs js/Error) err
           (println "compile-all error")
           #?(:clj  (println err)
              :cljs (js/console.error err))))
    (try
      (let [{:keys [delta-time total-time]} game
            world (swap! world/world*
                           #(-> %
                                (o/insert ::world/time
                                          {::world/total total-time
                                           ::world/delta delta-time})
                                o/fire-rules))
            {game-width :width game-height :height} (first (o/query-all world ::world/window))
            shader-esses (o/query-all world ::world/shader-esse)
            sprite-esses (o/query-all world ::world/sprite-esse)]
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
        (println "tick error")
        #?(:clj  (println err)
           :cljs (js/console.error err)))))
  game)
