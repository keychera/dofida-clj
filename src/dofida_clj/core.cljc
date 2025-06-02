(ns dofida-clj.core
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [dofida-clj.refresh :refer [*refresh?]]
   [dofida-clj.utils :as utils]
   [play-cljc.gl.core :as c]))

(defonce *state (atom {:esse/dofida nil}))

(def vertices
  [-1.0 1.0
   -1.0 -1.0
   1.0  1.0

   1.0 -1.0
   -1.0 -1.0
   1.0  1.0])

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

;; TODO deal with unordered maps (#keys > 8)
(defn deep-merge [a & maps]
  (if (map? a)
    (apply merge-with deep-merge a maps)
    (apply merge-with deep-merge maps)))

(def random-fns
  {:signatures '{random  ([vec2] float)
                 random2 ([vec2 float] float)
                 random3 ([vec2 float float] float)}
   :functions  '{random  ([st]     (fract (* (sin (dot st.xy (vec2 "12.9898" "78.233"))) "43758.5453123")))
                 random2 ([st l]   (random (vec2 (random st) l)))
                 random3 ([st l t] (random (vec2 (random2 st l) t)))}})

(def noise-fn
  {:signatures '{noise ([vec2] float)}
   :functions  '{noise ([st]
                        (=vec2 i (floor st))
                        (=vec2 f (fract st))
                        (=float a (random i))
                        (=float b (random (+ i (vec2 "1.0" "0.0"))))
                        (=float c (random (+ i (vec2 "0.0" "1.0"))))
                        (=float d (random (+ i (vec2 "1.0" "1.0"))))
                        (=vec2 u (* f f (- "3.0" (* "2.0" f))))
                        (+ (mix a b u.x)
                           (* (- c a) u.y (- "1.0" u.x))
                           (* (- d b) u.x u.y)))}})

(def box-fn
  {:signatures '{box ([vec2 vec2] float)}
   :functions  '{box ([_st _size]
                      (= _size (- (vec2 "0.5") (* _size "0.5")))
                      (=vec2 uv (* (smoothstep _size (+ _size (vec2 "0.001")) _st)
                                   (smoothstep _size (+ _size (vec2 "0.001")) (- (vec2 "1.0") _st))))
                      (* uv.x uv.y))}})

(def random-rect-fn
  {:signatures '{randomRect ([vec2 vec2] float)}
   :functions  '{randomRect ([_st _size]
                             (=vec2 st (fract _st))
                             (=vec2 toCenter (- (vec2 "0.5") st))
                             (=float angle (atan toCenter.y toCenter.x))
                             (+= _size (vec2 (* "0.5" (random (floor (- _st))))
                                             (* "0.1" (random (floor _st)))))
                             (=  _size (- (vec2 "0.5") (* _size "0.5")))
                             (=float rand (noise (* (+ (vec2 angle) st (floor _st)) "10.296")))
                             (=vec2 uv (* (smoothstep _size (+ _size (vec2 "0.001") (* "0.050" rand)) st)
                                          (smoothstep _size (+ _size (vec2 "0.001") (* "0.050" rand)) (- (vec2 "1.0") st))))
                             (* uv.x uv.y))}})

(def hell-main-fn
  {:signatures '{main ([] void)}
   :functions  '{main ([]
                       (=vec2 st (/ gl_FragCoord.xy u_resolution))
                       (*= st "3.0")
                       (=float rect (randomRect st (vec2 "0.310" "0.700")))
                       (=float rect2 (randomRect st (vec2 "0.340" "0.730")))

                       (=vec2 mouse (vec2 (/ u_mouse.x u_resolution.x)
                                          (/ (- u_resolution.y u_mouse.y) u_resolution.y)))
                       (=vec2 pos (vec2 (* (+ st (/ u_time "1.0") mouse)
                                           (+ "2.0" (* "0.01" (length mouse))))))

                       (=float layer "5.0")
                       (=vec3 red (vec3 "0.86" "0.878" "0.83"))
                       (=float noiseVal (/ (floor (* layer (noise pos))) layer))
                       (= o_color (vec4 (vec3 rect) "1.0"))
                       (*= o_color (vec4 (* red (vec3 noiseVal)) "1.0"))
                       (=float factor (smoothstep "0.5" "0.65" (distance (* mouse "3.0") st)))
                       (+= o_color (* (- "1.0" factor) (vec4 (vec3 (- rect2 rect)) "1.0"))))}})

(def perlin-fn
  {:signatures '{perlin ([vec2 float float] float)}
   :functions  '{perlin ([p dim time]
                         (=vec2 pos   (floor (* p dim)))
                         (=vec2 posx  (+ pos (vec2 "1.0" "0.0")))
                         (=vec2 posy  (+ pos (vec2 "0.0" "1.0")))
                         (=vec2 posxy (+ pos (vec2 "1.0")))

                         (=float c   (random3 pos   dim time))
                         (=float cx  (random3 posx  dim time))
                         (=float cy  (random3 posy  dim time))
                         (=float cxy (random3 posxy dim time))

                         (=vec2 d (fract (* p dim)))
                         (= d (+ (* "-0.5" (cos (* d "3.14159265358979323846"))) "0.5"))

                         (=float ccx   (mix c cx d.x))
                         (=float cycxy (mix cy cxy d.x))
                         (=float center (mix ccx cycxy d.y))

                         (- (* center "2.0") "1.0"))}})

(def fbm-fn
  {:signatures '{fbm ([vec2] float)}
   :functions  '{fbm ([st]
                      (=float value "0.0")
                      (=float amplitude "0.5")
                      (=float frequency "0.0")
                      ("for (int i = 0; i < 16; i++)"
                       (+= value (* amplitude (noise st)))
                       (*= st "2.4")
                       (*= amplitude "0.5"))
                      value)}})

(def star-main-fn
  {:signatures '{main ([] void)}
   :functions  '{main ([]
                       (=vec2 orig_xy gl_FragCoord.xy)
                       (=vec2 mouse (/ u_mouse (* u_resolution "20.0")))
                       (+= orig_xy (* "256.0" mouse))

                       (=vec2 st (/ orig_xy u_resolution.xy))
                       (*= st.x (/ u_resolution.x u_resolution.y))
                       (=vec3 color (vec3 "0.0"))

                       (+= color (* "0.7" (vec3 "0.3" "0.3" (mix (vec2 "0.5") (vec2 "0.8") st))))

                       (=vec2 smoke_pos (* st "2.0"))
                       (=float speed (/ u_time "16.0"))
                       (=float smoke (- (fbm (- smoke_pos speed)) (fbm (- (* smoke_pos "1.5") speed))))
                       (=float perlin_v (perlin (+ (* st "8.0") speed) "0.4" "0.0"))
                       (= smoke (mix "-0.1" smoke perlin_v))

                       (*= color (+ "0.7" smoke))

                       (=float sparkle "0.3")
                       (=float density "0.5")
                       (=vec2 pos0 (floor (* st "300.0")))
                       (=vec2 pos (+ (* st "512.0") (* sparkle (sin u_time))))
                       (+= color (* (vec3 (smoothstep (+ "0.85" (* "0.1" (random pos0))) "1.0" (perlin pos "0.604" "40.0")))
                                    (+ density (* (- "1.0" density) (vec3 (perlin (* st "8.0") "0.5" "40.0"))))))

                       (= o_color (vec4 color "1.0")))}})


(def fragment-shader
  (deep-merge 
   {:version    "300 es",
    :precision  "mediump float"
    :uniforms   '{u_resolution vec2
                  u_mouse      vec2
                  u_time       float}
    :inputs     '{v_position vec4}
    :outputs    '{o_color vec4}}
   random-fns noise-fn
  ;;  box-fn random-rect-fn hell-main-fn
   perlin-fn fbm-fn star-main-fn
   ))

(defn ->dofida [game]
  (let [[game-width game-height] (utils/get-size game)]
    {:vertex vertex-shader
     :fragment fragment-shader
     :attributes {'a_position {:data vertices
                               :type (gl game FLOAT)
                               :size 2}}
     :uniforms {'u_time 0.0
                'u_resolution [game-width game-height]
                'u_mouse [0.0 0.0]}}))

(defn mutate-dofida [{:keys [total-time]} {:keys [mouse-x mouse-y] :as state}]
  (-> state
      (assoc-in [:esse/dofida :uniforms 'u_time] total-time)
      (assoc-in [:esse/dofida :uniforms 'u_mouse] [(or mouse-x 0.0) (or mouse-y 0.0)])))

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
