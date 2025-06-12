(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.refresh :refer [*refresh?]]
   [engine.utils :as utils]
   [dofida.dofida :as dofida]
   [play-cljc.gl.core :as c]))

(defonce *state (atom {:esse/dofida nil}))

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
  (let [esse-dofida (c/compile game (dofida/->dofida game))]
    (swap! *state assoc :esse/dofida esse-dofida)))

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear {:color [(/ 5 255) (/ 4 255) (/ 10 255) 1] :depth 1}})

(defn tick [game]
  (if @*refresh?
    (try (println "calling (init game)")
         (swap! *refresh? not)
         (init game)
         (catch #?(:clj Exception :cljs js/Error) err
           (println err)))
    (let [{:esse/keys [dofida]} @*state
          [game-width game-height] (utils/get-size game)]
      (when (and (pos? game-width) (pos? game-height))
        (c/render game (update screen-entity :viewport
                               assoc :width game-width :height game-height))
        (c/render game dofida)
        (swap! *state (fn [state] (dofida/mutate-dofida game state))))))
  game)
