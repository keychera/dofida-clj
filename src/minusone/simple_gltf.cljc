(ns minusone.simple-gltf
  (:require
   #?(:clj [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [engine.world :as world]
   [minusone.esse :refer [esse]]
   [minusone.rules.anime.anime :as anime]
   [minusone.rules.gl.gl :refer [GL_TRIANGLES GL_TEXTURE0 GL_TEXTURE_2D]]
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

(def pos+skins-vert
  {:precision  "mediump float"
   :inputs     '{POSITION   vec3
                 WEIGHTS_0  vec4
                 JOINTS_0   uvec4}
   :uniforms   '{u_model      mat4
                 u_view       mat4
                 u_projection mat4
                 u_joint_mats [mat4 2]}
   :signatures '{main ([] void)}
   :functions  '{main ([]
                       (=vec4 pos (vec4 POSITION "1.0"))
                       (=mat4 skin_mat
                              (+ (* WEIGHTS_0.x [u_joint_mats (.x JOINTS_0)])
                                 (* WEIGHTS_0.y [u_joint_mats (.y JOINTS_0)])))
                       (=vec4 world_pos (* u_model skin_mat pos))
                       (=vec4 cam_pos (* u_view world_pos))
                       (= gl_Position (* u_projection cam_pos)))}})

(def position-vert
  {:precision  "mediump float"
   :inputs     '{POSITION   vec3}
   :uniforms   '{u_model      mat4
                 u_view       mat4
                 u_projection mat4}
   :signatures '{main ([] void)}
   :functions  '{main ([]
                       (=vec4 pos (vec4 POSITION "1.0"))
                       (= gl_Position (* u_projection u_view u_model pos)))}})

(def white-frag
  {:precision  "mediump float"
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions  '{main ([] (= o_color (vec4 "1.0")))}})

(def pos+tex+skin-vert
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
                       (= gl_Position (* u_projection u_view u_model skin_mat pos))
                       (= Normal NORMAL)
                       (= TexCoords TEXCOORD_0))}})

(def tex-frag
  {:precision  "mediump float"
   :inputs     '{Normal vec3
                 TexCoords vec2}
   :outputs    '{o_color vec4}
   :uniforms   '{u_mat_diffuse sampler2D}
   :signatures '{main ([] void)}
   :functions  '{main ([]
                       (=vec3 result (.rgb (texture u_mat_diffuse TexCoords)))
                       (= o_color (vec4 result "1.0")))}})

(defn init-fn [world game]
  (-> world
      (esse ::joints-shader #::shader{:program-data (shader/create-program game pos+skins-vert white-frag)})
      (esse ::simpleskin
            #::assimp{:model-to-load ["assets/simpleskin.gltf"] :tex-unit-offset 3}
            #::shader{:use ::joints-shader})
      (esse ::position-shader #::shader{:program-data (shader/create-program game position-vert white-frag)})
      (esse ::simpleanime
            #::assimp{:model-to-load ["assets/simpleanime.gltf"] :tex-unit-offset 21}
            #::shader{:use ::position-shader})
      (esse ::gltf-shader1 #::shader{:program-data (shader/create-program game pos+tex+skin-vert tex-frag)})
      (esse ::rubah
            #::assimp{:model-to-load ["assets/fox.glb"] :tex-unit-offset 22}
            #::shader{:use ::gltf-shader1})))

(defn after-load-fn [world _game]
  (-> world
      (esse ::simpleskin  t3d/default
            #::t3d{:position (v/vec3 -5.5 0.0 -16.0)
                   :scale (v/vec3 8.0)})
      (esse ::simpleanime t3d/default
            #::t3d{:position (v/vec3 5.5 0.0 -32.0)
                   :scale (v/vec3 8.0)})
      (esse ::rubah t3d/default
            #::t3d{:position (v/vec3 0.0 0.0 0.0)
                   :scale (v/vec3 0.1)})))

(def rules
  (o/ruleset
   {::model-w-joints
    [:what
     [esse-id ::gltf/data gltf-data {:then false}]
     [esse-id ::gltf/primitives primitives {:then false}]
     [esse-id ::gltf/transform-db transform-db {:then false}]
     [esse-id ::gltf/inv-bind-mats inv-bind-mats {:then false}]
     [esse-id ::t3d/position position]
     [esse-id ::t3d/scale scale]
     [esse-id ::shader/use shader-id]
     [shader-id ::shader/program-data program-data]
     [::world/global ::projection/matrix projection]
     [::firstperson/player ::firstperson/look-at look-at {:then false}]
     [::firstperson/player ::firstperson/position cam-position {:then false}]
     :then
     (println esse-id "all set!")]}))

(defn render-fn [world game]
  (doseq [{:keys [esse-id primitives position scale transform-db inv-bind-mats] :as esse}
          (o/query-all world ::model-w-joints)]
    (let [gltf-data     (:gltf-data esse)
          accessors     (:accessors gltf-data)
          program-data  (:program-data esse)
          program       (:program program-data)

          uni-loc       (:uni-locs program-data)
          u_model       (get uni-loc 'u_model)
          u_view        (get uni-loc 'u_view)
          u_projection  (get uni-loc 'u_projection)
          u_joint_mats  (get uni-loc 'u_joint_mats)
          u_mat_diffuse (get uni-loc 'u_mat_diffuse)

          view          (f32-arr (vec (:look-at esse)))
          project       (f32-arr (vec (:projection esse)))
          skin          (first (:skins gltf-data))
          anime         (get (::anime/interpolated @anime/db*) esse-id)
          transform-db  (reduce
                         (fn [t-db [target-node value]]
                           (let [next-translation (get value "translation")
                                 next-rotation    (get value "rotation")
                                 next-scale       (get value "scale")]
                             (if (or next-translation next-rotation next-scale)
                               (update t-db target-node
                                       (fn [{:keys [translation rotation scale] :as transform}]
                                         (let [trans-mat (m-ext/translation-mat (m/+ translation next-translation))
                                               rotate    (m/* (or next-rotation (q/quat)) rotation)
                                               rot-mat   (g/as-matrix rotate)
                                               scale-mat (m-ext/scaling-mat (nth scale 0) (nth scale 1) (nth scale 2))
                                               global-t  (reduce m/* [trans-mat rot-mat scale-mat])]
                                           (assoc transform :global-transform global-t))))
                               t-db)))
                         transform-db anime)
          joint-mats    (gltf/create-joint-mats-arr skin transform-db inv-bind-mats)]
      (when (= esse-id ::rubah)
        #_{:clj-kondo/ignore [:inline-def]}
        (def hmm esse))
      (gl game useProgram program)
      (gl game uniformMatrix4fv u_view false view)
      (gl game uniformMatrix4fv u_projection false project)
      (when (and joint-mats (> (.-length joint-mats) 0))
        (gl game uniformMatrix4fv u_joint_mats false joint-mats))
      (doseq [prim primitives]
        (let [vao (get @vao/db* (:vao-name prim))
              tex (get @texture/db* (:tex-name prim))]
          (when vao
            (let [indices      (get accessors (:indices prim))
                  node-0-trans (:global-transform (get transform-db 0))
                  trans-mat    (m-ext/translation-mat position)
                  rot-mat      (g/as-matrix (q/quat-from-axis-angle
                                             (v/vec3 1.0 0.0 0.0)
                                             (m/radians 0.0)))
                  scale-mat    (m-ext/scaling-mat (nth scale 0) (nth scale 1) (nth scale 2))

                  model        (reduce m/* [trans-mat rot-mat scale-mat node-0-trans])
                  model        (f32-arr (vec model))]
              (gl game bindVertexArray vao)
              (gl game uniformMatrix4fv u_model false model)

              (when-let [{:keys [tex-unit texture]} tex]
                (gl game activeTexture (+ GL_TEXTURE0 tex-unit))
                (gl game bindTexture GL_TEXTURE_2D texture)
                (gl game uniform1i u_mat_diffuse tex-unit))

              (gl game drawElements GL_TRIANGLES (:count indices) (:componentType indices) 0)
              (gl game bindVertexArray #?(:clj 0 :cljs nil)))))))))

(def system
  {::world/init-fn init-fn
   ::world/after-load-fn after-load-fn
   ::world/render-fn render-fn
   ::world/rules rules})

(comment

  (eduction
   (filter #(= (:esse-id %) ::rubah))
   (filter #(= (:anime-name %) "Walk")) 
   (map :target-path)
   (distinct)
   (::anime/animes @anime/db*)) 

  (:transform-db hmm)

  :-)