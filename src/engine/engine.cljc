(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [dofida.dofida :as dofida]
   [engine.esse :as esse]
   [engine.refresh :refer [*refresh?]]
   [engine.session :as session]
   [engine.utils :as utils]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as entities-2d]
   [play-cljc.transforms :as t]))

(defonce *state
  (atom {:esse/dofida2 nil}))

(defn init2 [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
  (utils/get-image
   "dofida2.png"
   (fn [{:keys [data width height]}]
     (let [entity (entities-2d/->image-entity game data width height)
           entity (c/compile game entity)
           [game-width game-height] (utils/get-size game)
           esse-dofida2 (-> entity
                            (assoc :width width :height height
                                   :viewport {:x 0, :y 0, :width (- game-width), :height game-height})
                            (t/translate 0 -0.1))]
       (swap! *state assoc :esse/dofida2 esse-dofida2)))))


(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
  (let [[game-width game-height] (utils/get-size game)]
    (reset! session/*session
            (-> session/initial-session
                (o/insert ::dofida (esse/->shader)) ;; I don't know the necessity of this yet
                (o/insert ::session/window
                          {::session/width game-width
                           ::session/height game-height})
                (o/fire-rules))))
  (let [esse-dofida (c/compile game (dofida/->dofida game))]
    (swap! session/*session
           #(-> %
                (o/insert ::dofida ::esse/compiled-shader esse-dofida)
                (o/fire-rules)))))

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear {:color [(/ 0 255) (/ 0 255) (/ 0 255) 1] :depth 1}})

(defn tick [game]
  (if @*refresh?
    (try (println "calling (init game)")
         (swap! *refresh? not)
         (init game)
         (catch #?(:clj Exception :cljs js/Error) err
           (println "init error")
           (println err)))
    (try
      (let [{:keys [delta-time total-time]} game
            session (swap! session/*session
                           #(-> %
                                (o/insert ::session/time
                                          {::session/total total-time
                                           ::session/delta delta-time})
                                o/fire-rules))
            {game-width :width game-height :height} (first (o/query-all session ::session/window))
            shader-esses (o/query-all session ::session/shader-esse)]
        (when (and (pos? game-width) (pos? game-height))
          (c/render game (update screen-entity :viewport assoc :width game-width :height game-height))
          (doseq [shader-esse shader-esses]
            (c/render game (:compiled-shader shader-esse)))))
      (catch #?(:clj Exception :cljs js/Error) err
        (println "tick error")
        (println err))))
  game)

