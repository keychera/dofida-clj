(ns minusone.rubahperak
  (:require
   #?(:clj [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [engine.world :as world]
   [minusone.esse :refer [esse]]
   [minusone.rules.gl.gltf :as gltf] 
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.texture :as texture]
   [minusone.rules.gl.vao :as vao]
   [minusone.rules.model.assimp :as assimp]
   [minusone.rules.projection :as projection]
   [minusone.rules.transform3d :as t3d]
   [minusone.rules.view.firstperson :as firstperson]
   [odoyle.rules :as o]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(def pmx-vert
  {:precision  "mediump float"
   :inputs     '{POSITION   vec3
                 NORMAL     vec3
                 TEXCOORD_0 vec2
                 JOINTS_0 vec4
                 WEIGHTS_0 vec4}
   :outputs    '{FragPos vec3
                 Normal vec3
                 TexCoords vec2}
   :uniforms   '{u_mvp mat4}
   :signatures '{main ([] void)}
   :functions  '{main ([]
                       (=vec4 dummy (- JOINTS_0.xyzw WEIGHTS_0.xyzw)) ;; to make it not trimmed by shader compiler
                       (=vec4 dummy2 (- WEIGHTS_0.xyzw JOINTS_0.xyzw))
                       (= gl_Position (* u_mvp (+ (vec4 POSITION "1.0") dummy dummy2)))
                       (= FragPos POSITION)
                       (= Normal NORMAL)
                       (= TexCoords TEXCOORD_0))}})

(def pmx-frag
  {:precision  "mediump float"
   :inputs     '{FragPos vec3
                 Normal vec3
                 TexCoords vec2}
   :outputs    '{o_color vec4}
   :uniforms   '{u_mat_diffuse sampler2D}
   :functions
   "
void main() 
{
    vec3 result = texture(u_mat_diffuse, TexCoords).rgb;
    o_color = vec4(result, 1.0);
}"})

(defn init-fn [world game]
  (-> world
      (esse ::pmx-shader #::shader{:program-data (shader/create-program game pmx-vert pmx-frag)})
      (esse ::rubahperak
              #::assimp{:model-to-load ["assets/models/SilverWolf/银狼.pmx"] :tex-unit-offset 3}
              #::shader{:use ::pmx-shader})
      (esse ::topaz
            #::assimp{:model-to-load ["assets/models/TopazAndNumby/Topaz.pmx"] :tex-unit-offset 8}
            #::shader{:use ::pmx-shader})
      (esse ::numby
              #::assimp{:model-to-load ["assets/models/TopazAndNumby/Numby.pmx"] :tex-unit-offset 13}
              #::shader{:use ::pmx-shader})))

(defn after-load-fn [world _game]
  (-> world
      (esse ::rubahperak 
            #::t3d{:position (v/vec3 -5.5 0 0.0)})
      (esse ::topaz
            #::t3d{:position (v/vec3 5.5 0 0.0)})
      (esse ::numby
            #::t3d{:position (v/vec3 0 0 0.0)})))

(def rules
  (o/ruleset
   {::pmx-models
    [:what
     [esse-id ::assimp/gltf gltf-json {:then false}]
     [esse-id ::gltf/primitives primitives {:then false}]
     [esse-id ::t3d/position position]
     [::pmx-shader ::shader/program-data program-data]
     [::world/global ::projection/matrix projection]
     [::firstperson/player ::firstperson/look-at look-at {:then false}]
     [::firstperson/player ::firstperson/position cam-position {:then false}]
     :then
     (println esse-id "all set!")]}))

(defn render-fn [world game]
  (doseq [{:keys [primitives position] :as esse} (o/query-all world ::pmx-models)]
    (let [gltf-json (:gltf-json esse)
          accessors     (:accessors gltf-json)
          program-data  (:program-data esse)
          program       (:program program-data)
          uni-loc       (:uni-locs program-data)
          u_mvp         (get uni-loc 'u_mvp)
          u_mat_diffuse (get uni-loc 'u_mat_diffuse)]
      (doseq [prim primitives]
        (let [vao  (get @vao/db* (:vao-name prim))
              tex  (get @texture/db* (:tex-name prim))]
          (when (and vao tex)
            (let [indices       (get accessors (:indices prim))
                  view          (:look-at esse)
                  project       (:projection esse)
                  [^float x
                   ^float y
                   ^float z]    position
                  trans-mat     (m-ext/translation-mat x y z)
                  rot-mat       (g/as-matrix (q/quat-from-axis-angle
                                              (v/vec3 0.0 1.0 0.0)
                                              (m/radians 0.18)))
                  scale-mat     (m-ext/scaling-mat 1.0)

                  model         (reduce m/* [trans-mat rot-mat scale-mat])
                  
                  mvp           (reduce m/* [project view model])
                  mvp           (f32-arr (vec mvp))]
              (gl game useProgram program)
              (gl game bindVertexArray vao)
              (gl game uniformMatrix4fv u_mvp false mvp)

              (let [{:keys [tex-unit texture]} tex]
                (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
                (gl game bindTexture (gl game TEXTURE_2D) texture)
                (gl game uniform1i u_mat_diffuse tex-unit))

              (gl game drawElements
                  (gl game TRIANGLES)
                  (:count indices)
                  (:componentType indices)
                  0)
              (gl game bindVertexArray #?(:clj 0 :cljs nil)))))))))

(def system
  {::world/init-fn init-fn
   ::world/after-load-fn after-load-fn
   ::world/render-fn render-fn
   ::world/rules rules})
