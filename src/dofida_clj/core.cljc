(ns dofida-clj.core
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
               :cljs [play-cljc.macros-js :refer-macros [gl math]])
   [dofida-clj.refresh :refer [*refresh?]]
   [dofida-clj.utils :as utils]
   [play-cljc.gl.core :as c]))

(defonce *state (atom {:esse/dofida nil}))

(def vertices
  [-0.5 -0.5
   -0.5 -0.2
   0.0  0.5])

(def vertex-shader
  '{:version "300 es"
    :precision "mediump float"
    :uniforms {u_time float}
    :inputs {a_position vec2},
    :outputs {v_color vec4},
    :signatures {main ([] void)},
    :functions
    {main
     ([]
      (= gl_Position (vec4 (.x a_position) (.y a_position) "0.0" "1.0"))
      (=float dampened_time (* 2.5 u_time))
      (= v_color (+ (* gl_Position 0.5) (sin dampened_time))))}})

(def fragment-shader
  '{:precision "mediump float"
    :inputs {v_color vec4}
    :outputs {o_color vec4}
    :signatures {main ([] void)},
    :functions {main ([] (= o_color v_color))}})

(defn ->dofida [game]
  {:vertex vertex-shader
   :fragment fragment-shader
   :attributes {'a_position {:data vertices
                             :type (gl game FLOAT)
                             :size 2}}
   :uniforms {'u_time 0.0}})

(defn mutate-dofida [{:keys [total-time]} state]
  (assoc-in state [:esse/dofida :uniforms 'u_time] total-time))

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
  (let [esse-dofida (c/compile game (->dofida game))]
    (swap! *state assoc :esse/dofida esse-dofida)))

(def screen-entity
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear {:color [(/ 173 255) (/ 216 255) (/ 230 255) 1] :depth 1}})

(defn tick [game]
  (if @*refresh?
   (do (println "recompiling")
       (swap! *refresh? not)
       (init game))
    (let [{:esse/keys [dofida]} @*state
          [game-width game-height] (utils/get-size game)]
      (when (and (pos? game-width) (pos? game-height))
        (c/render game (update screen-entity :viewport
                               assoc :width game-width :height game-height))
        (c/render game dofida)
        (swap! *state (fn [state] (mutate-dofida game state))))))
  game)
