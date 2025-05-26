(ns dofida-clj.core
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [dofida-clj.refresh :refer [*refresh?]]
   [dofida-clj.utils :as utils]
   [play-cljc.gl.core :as c]))

(defonce *state (atom {:esse/dofida nil}))

(def vertices
  [0.0 0.0
   -0.5 -0.2
   0.0  0.5

   0.1 0.05
   0.5 0.2
   0.1  0.5

   -0.5 -0.3
   0.5 0.1
   0.1  -0.5])

(def vertex-shader
  '{:version "300 es"
    :precision "mediump float"
    :uniforms {u_time float}
    :inputs {a_position vec2}
    :outputs {}
    :signatures {main ([] void)}
    :functions
    {main ([]
           (= gl_Position (vec4 (.x a_position) (.y a_position) "0.0" "1.0")))}})

(def fragment-shader
  '{:precision "mediump float"
    :uniforms {u_resolution vec2
               u_time float}
    :inputs {v_position vec4}
    :outputs {o_color vec4}
    :signatures {plot ([vec2 float] float)
                 main ([] void)},
    :functions {plot ([st pct]
                      (- (smoothstep (- pct "0.02") pct st.y)
                         (smoothstep pct (+ pct "0.02") st.y)))
                main ([]
                      (=vec2 st (/ gl_FragCoord.xy u_resolution))
                      (=float y st.x)
                      (=vec3 color (vec3 y))
                      (=float pct (plot st, y))
                      (= color (+ (* (- "1.0" pct) color) (* pct (vec3 "0.0" "1.0" "0.0"))))
                      (= o_color (vec4 color "1.0")))}})

(defn ->dofida [game]
  (let [[game-width game-height] (utils/get-size game)]
    {:vertex vertex-shader
     :fragment fragment-shader
     :attributes {'a_position {:data vertices
                               :type (gl game FLOAT)
                               :size 2}}
     :uniforms {'u_time 0.0
                'u_resolution [game-width game-height]}}))

(defn mutate-dofida [{:keys [total-time]} state]
  (assoc-in state [:esse/dofida :uniforms 'u_time] total-time))

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
  (let [esse-dofida (c/compile game (->dofida game))]
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
        (swap! *state (fn [state] (mutate-dofida game state))))))
  game)
