(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [dofida.dofida :as dofida]
   [engine.refresh :refer [*refresh?]]
   [engine.utils :as utils]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as entities-2d]
   [play-cljc.transforms :as t]))

(defonce *state
  (atom {:esse/dofida  nil
         :esse/dofida2 nil}))

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
  (let [esse-dofida (c/compile game (dofida/->dofida game))]
    (swap! *state assoc :esse/dofida esse-dofida))
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

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear {:color [(/ 0 255) (/ 0 255) (/ 0 255) 1] :depth 1}})

(defn tick [game]
  (if @*refresh?
    (try (println "calling (init game)")
         (swap! *refresh? not)
         (init game)
         (catch #?(:clj Exception :cljs js/Error) err
           (println err)))
    (let [{:esse/keys [dofida dofida2]} @*state
          [game-width game-height] (utils/get-size game)]
      (when (and (pos? game-width) (pos? game-height))
        (c/render game (update screen-entity :viewport assoc :width game-width :height game-height))
        (c/render game dofida)
        (c/render game dofida2)
        (swap! *state (fn [state] (dofida/mutate-dofida game state))))))
  game)
