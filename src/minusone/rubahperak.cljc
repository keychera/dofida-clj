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
   [thi.ng.math.core :as m]
   [clojure.string :as str]
   [com.rpl.specter :as sp]
   [com.rpl.specter :as s]))

(def pmx-vert
  {:precision  "mediump float"
   :inputs     '{POSITION   vec3
                 NORMAL     vec3
                 TEXCOORD_0 vec2
                 WEIGHTS_0  vec4
                 JOINTS_0   uvec4}
   :outputs    '{Normal    vec3
                 TexCoords vec2}
   :uniforms   '{u_model      mat4
                 u_view       mat4
                 u_projection mat4
                 u_joint_mats [mat4 500]}
   :signatures '{main ([] void)}
   :functions  '{main ([]
                       (=vec4 pos (vec4 POSITION "1.0"))
                       (=mat4 skin_mat
                              (+ (* WEIGHTS_0.x [u_joint_mats JOINTS_0.x])
                                 (* WEIGHTS_0.y [u_joint_mats JOINTS_0.y])
                                 (* WEIGHTS_0.z [u_joint_mats JOINTS_0.z])
                                 (* WEIGHTS_0.w [u_joint_mats JOINTS_0.w])))
                       (=vec4 world_pos (* u_model skin_mat pos))
                       (=vec4 cam_pos (* u_view world_pos))
                       (= gl_Position (* u_projection cam_pos))
                       (= Normal NORMAL)
                       (= TexCoords TEXCOORD_0))}})

(def pmx-frag
  {:precision  "mediump float"
   :inputs     '{Normal vec3
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
     [esse-id ::gltf/transform-db transform-db {:then false}]
     [esse-id ::gltf/inv-bind-mats inv-bind-mats {:then false}]
     [esse-id ::t3d/position position]
     [::pmx-shader ::shader/program-data program-data]
     [::world/global ::projection/matrix projection]
     [::firstperson/player ::firstperson/look-at look-at {:then false}]
     [::firstperson/player ::firstperson/position cam-position {:then false}]
     :then
     (println esse-id "all set!" (count inv-bind-mats))]}))

(defn render-fn [world game]
  (doseq [{:keys [primitives position transform-db inv-bind-mats] :as esse} (o/query-all world ::pmx-models)]
    (let [esse-id       (:esse-id esse)
          gltf-json     (:gltf-json esse)
          accessors     (:accessors gltf-json)
          program-data  (:program-data esse)
          program       (:program program-data)

          uni-loc       (:uni-locs program-data)
          u_model       (get uni-loc 'u_model)
          u_view        (get uni-loc 'u_view)
          u_projection  (get uni-loc 'u_projection)
          u_mat_diffuse (get uni-loc 'u_mat_diffuse)
          u_joint_mats  (get uni-loc 'u_joint_mats)

          view          (f32-arr (vec (:look-at esse)))
          project       (f32-arr (vec (:projection esse)))
          skin          (first (:skins gltf-json))
          _             (when (= esse-id ::rubahperak) (def hmm transform-db))
          time          (:total-time game)
          factor        (Math/sin (/ time 128))
          transform-db  (if (= esse-id ::rubahperak)
                          (sp/transform [(s/multi-path 9 10 12) :global-transform]
                                        (fn [gt] (->> gt
                                                      (m/* (m-ext/translation-mat (* 0.1 factor) 0.0 0.0))))
                                        transform-db)
                          transform-db)
          joint-mats    (gltf/create-joint-mats-arr skin transform-db inv-bind-mats)]
      (gl game useProgram program)
      (gl game uniformMatrix4fv u_view false view)
      (gl game uniformMatrix4fv u_projection false project)
      (gl game uniformMatrix4fv u_joint_mats false joint-mats)
      (doseq [prim primitives]
        (let [vao (get @vao/db* (:vao-name prim))
              tex (get @texture/db* (:tex-name prim))]
          (when (and vao tex)
            (let [indices   (get accessors (:indices prim))

                  [^float x
                   ^float y
                   ^float z]   position
                  trans-mat (m-ext/translation-mat x y z)
                  rot-mat   (g/as-matrix (q/quat-from-axis-angle
                                          (v/vec3 0.0 1.0 0.0)
                                          (m/radians 0.18)))
                  scale-mat (m-ext/scaling-mat 1.0)

                  model     (reduce m/* [trans-mat rot-mat scale-mat])
                  model     (f32-arr (vec model))]
              (gl game bindVertexArray vao)
              (gl game uniformMatrix4fv u_model false model)

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

(comment
  (eduction
   (map (fn [[idx bone]] (assoc bone :idx idx)))
   hmm) 

  [(->> hmm
        (sp/select-one [191])
        :global-transform
        vec)
   (->> hmm
       (sp/transform [191 :global-transform]
                     (fn [gt] (m/* (m-ext/translation-mat 1.0 0.0 0.0) gt)))
       (sp/select-one [191])
       :global-transform
       vec)])