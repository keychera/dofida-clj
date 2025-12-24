(ns minustwo.stage.wirecube
  (:require
   [engine.game :as game]
   [engine.world :as world :refer [esse]]
   [minustwo.anime.pose :as pose]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.shader :as shader]
   [minustwo.model.assimp :as assimp]
   [minustwo.stage.hidup :as hidup]
   [minustwo.systems.transform3d :as t3d]))

(def the-vertex-shader
  {:precision  "mediump float"
   :inputs     '{POSITION   vec3
                 NORMAL     vec3
                 TEXCOORD_0 vec2}
   :outputs    '{Normal    vec3
                 TexCoords vec2}
   :uniforms   '{u_model      mat4
                 u_view       mat4
                 u_projection mat4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position (* u_projection u_view u_model (vec4 POSITION "1.0")))
           (= Normal NORMAL)
           (= TexCoords TEXCOORD_0))}})

(def the-fragment-shader
  {:precision  "mediump float"
   :inputs     '{Normal    vec3
                 TexCoords vec2}
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([] (= o_color (vec4 "0.9" "0.2" "0.2" 0.7)))}})

(defn init-fn [world game]
  (let [ctx (game/gl-ctx game)]
    (-> world
        (esse ::simpleshader
              #::shader{:program-info (cljgl/create-program-info-from-iglu ctx the-vertex-shader the-fragment-shader)})
        (esse ::wirecube
              #::assimp{:model-to-load ["assets/wirebeing.glb"] :config {:tex-unit-offset 0}}
              #::shader{:use ::simpleshader}
              pose/default
              hidup/normal-draw
              t3d/default))))

(def system
  {::world/init-fn init-fn})