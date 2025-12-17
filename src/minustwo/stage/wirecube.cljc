(ns minustwo.stage.wirecube
  (:require
   [engine.game :as game]
   [engine.sugar :refer [f32-arr]]
   [engine.world :as world :refer [esse]]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_FLOAT]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.model.assimp :as assimp]
   [minustwo.stage.hidup :as hidup]
   [minustwo.systems.transform3d :as t3d]
   [thi.ng.geom.vector :as v]))

;; https://tchayen.github.io/posts/wireframes-with-barycentric-coordinates
(def barycentric-shader
  {:precision  "mediump float"
   :inputs     '{POSITION      vec3
                 NORMAL        vec3
                 TEXCOORD_0    vec2
                 a_barycentric vec3}
   :outputs    '{vbc       vec3
                 Normal    vec3
                 TexCoords vec2}
   :uniforms   '{u_model      mat4
                 u_view       mat4
                 u_projection mat4}
   :signatures '{main ([] void)}
   :functions  '{main ([]
                       (= gl_Position (* u_projection u_view u_model (vec4 POSITION "1.0")))
                       (= vbc a_barycentric)
                       (= Normal NORMAL)
                       (= TexCoords TEXCOORD_0))}})

(def the-fragment-shader
  {:precision  "mediump float"
   :inputs     '{vbc       vec3
                 Normal    vec3
                 TexCoords vec2}
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           ("if(vbc.x < 0.01 || vbc.y < 0.01 || vbc.z < 0.01)"
            (= o_color (vec4 0.6 0.9 0.9 1.0)))
           ("else"
            (= o_color (vec4 0.0))))}})

(defn calc-barycentric [length]
  (let [n    (/ length 6)
        f32s (f32-arr (* n 9))]
    (dotimes [i n]
      (aset f32s (+ (* i 9) 0) 1.0)
      (aset f32s (+ (* i 9) 1) 0.0)
      (aset f32s (+ (* i 9) 2) 0.0)

      (aset f32s (+ (* i 9) 3) 0.0)
      (aset f32s (+ (* i 9) 4) 1.0)
      (aset f32s (+ (* i 9) 5) 0.0)

      (aset f32s (+ (* i 9) 6) 0.0)
      (aset f32s (+ (* i 9) 7) 0.0)
      (aset f32s (+ (* i 9) 8) 1.0))
    f32s))

(defonce barycentric-arr (calc-barycentric 36))

(defn add-barycentric [spell]
  (let [[left right] (split-with (complement :unbind-vao) spell)
        bary-spell [{:buffer-data barycentric-arr :buffer-type GL_ARRAY_BUFFER}
                    {:point-attr 'a_barycentric, :component-type GL_FLOAT, :count 3, :offset 0, :use-shader ::simpleshader}]]
    (into [] (mapcat identity) [left bary-spell right])))

(defn init-fn [world _game]
  (-> world
      (esse ::wirecube
            #::assimp{:model-to-load ["assets/cube.glb"] :tex-unit-offset 0}
            #::shader{:use ::simpleshader}
            #::gl-magic{:custom-spell-fn add-barycentric}
            hidup/normal-draw
            t3d/default)))

(defn after-load-fn [world game]
  (-> world
      (esse ::simpleshader
            #::shader{:program-info (cljgl/create-program-info (game/gl-ctx game) barycentric-shader the-fragment-shader)})
      (esse ::wirecube
            #::t3d{:translation (v/vec3 0.0 30.0 0.0)
                   :scale (v/vec3 5.0)})))

(def system
  {::world/init-fn init-fn
   ::world/after-load-fn after-load-fn})

(comment

  (-> @gltf/debug-data* ::wirecube :gltf-data :accessors)

  :-)