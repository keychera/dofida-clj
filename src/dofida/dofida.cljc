(ns dofida.dofida
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.utils :as utils]
   [play-cljc.math :as m]
   [play-cljc.primitives-2d :as primitives]
   [play-cljc.gl.entities-2d :as entities-2d]))


(def vertex-shader
  '{:version "300 es"
    :precision "mediump float"
    :uniforms {u_matrix mat3}
    :inputs {a_position vec2}
    :outputs {}
    :signatures {main ([] void)}
    :functions
    {main ([] (= gl_Position (vec4 (.xy (* u_matrix (vec3 a_position 1))) 0 1)))}})

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
                       (=vec2 mouse (/ u_mouse (* u_resolution "5.0")))
                       (+= orig_xy (* "256.0" mouse))

                       (=vec2 st (/ orig_xy u_resolution.xy))
                       (*= st.x (/ u_resolution.x u_resolution.y))
                       (=vec3 color (vec3 "0.0"))

                       (+= color (* "0.7" (vec3 u_sky_color.r 
                                                u_sky_color.g 
                                                (mix (vec2 (- u_sky_color.b "0.2")) (vec2 (+ u_sky_color.b "0.2")) st)))) 

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
  (utils/merge-shader-fn
   {:version   "300 es",
    :precision "mediump float"
    :uniforms  '{u_resolution vec2
                 u_sky_color  vec3
                 u_mouse      vec2
                 u_time       float}
    :inputs    '{v_position vec4}
    :outputs   '{o_color vec4}}
   random-fns noise-fn perlin-fn fbm-fn star-main-fn))

(defn ->dofida [game]
  (let [[game-width game-height] (utils/get-size game)]
    (-> {:vertex     vertex-shader
         :fragment   fragment-shader
         :attributes {'a_position {:data primitives/rect
                                   :type (gl game FLOAT)
                                   :size 2}}
         :uniforms   {'u_matrix     (m/identity-matrix 3)
                      'u_sky_color  [0.78 0.47 0.47]
                      'u_time       0.0
                      'u_resolution [game-width game-height]
                      'u_mouse      [0.0 0.0]}}
        (entities-2d/map->TwoDEntity))))
