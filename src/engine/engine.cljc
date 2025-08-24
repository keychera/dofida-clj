(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.esse :as esse]
   [engine.refresh :refer [*refresh?]]
   [engine.world :as session]
   [engine.utils :as utils]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as entities-2d]
   [play-cljc.transforms :as t]))

(defn update-window-size! [width height]
  (swap! session/world* o/insert ::session/window {::session/width width ::session/height height}))

(defn update-mouse-coords! [x y]
  (swap! session/world* o/insert ::session/mouse {::session/x x ::session/y y}))

(defn compile-shader [game session*]
  (doseq [{:keys [esse-id compile-fn]} (o/query-all @session* ::session/compile-shader)]
    (swap! session* #(o/insert % esse-id ::esse/compiling-shader true))
    (let [compiled-shader (compile-fn game)]
      (swap! session* #(-> %
                           (o/retract esse-id ::esse/compiling-shader)
                           (o/insert esse-id ::esse/compiled-shader compiled-shader)
                           (o/fire-rules))))))

(defn load-image [game session*]
  (doseq [{:keys [esse-id image-path]} (o/query-all @session* ::session/load-image)]
    (swap! session* #(o/insert % esse-id ::esse/loading-image true))
    (println "loading image" esse-id image-path)
    (utils/get-image
     image-path
     (fn [{:keys [data width height]}]
       (let [image-entity (entities-2d/->image-entity game data width height)
             image-entity (c/compile game image-entity)
             loaded-image (assoc image-entity :width width :height height)]
         (swap! session*
                #(-> %
                     (o/retract esse-id ::esse/loading-image)
                     (o/insert esse-id ::esse/current-sprite loaded-image)
                     (o/fire-rules))))))))

(defn compile-all [game session*]
  (compile-shader game session*)
  (load-image game session*))

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game ONE) (gl game ONE_MINUS_SRC_ALPHA))
  (let [[game-width game-height] (utils/get-size game)]
    (reset! session/world*
            (-> session/dofida-session
                (o/insert ::session/window
                          {::session/width game-width
                           ::session/height game-height})
                (o/fire-rules)))
    (compile-all game session/world*)))

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear {:color [(/ 0 255) (/ 0 255) (/ 0 255) 1] :depth 1}})


(defn tick [game]
  (if @*refresh?
    (try (println "calling (compile-all game)")
         (swap! *refresh? not)
         (init game)
         (compile-all game session/world*)
         (catch #?(:clj Exception :cljs js/Error) err
           (println "compile-all error")
           #?(:clj  (println err)
              :cljs (js/console.error err))))
    (try
      (let [{:keys [delta-time total-time]} game
            session (swap! session/world*
                           #(-> %
                                (o/insert ::session/time
                                          {::session/total total-time
                                           ::session/delta delta-time})
                                o/fire-rules))
            {game-width :width game-height :height} (first (o/query-all session ::session/window))
            shader-esses (o/query-all session ::session/shader-esse)
            sprite-esses (o/query-all session ::session/sprite-esse)]
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
