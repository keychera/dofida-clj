(ns minusone.simple-gltf
  (:require
   #?(:clj [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [engine.world :as world]
   [minusone.esse :refer [esse]]
   [minusone.rules.gl.gl :refer [GL_TRIANGLES]]
   [minusone.rules.gl.gltf :as gltf]
   [minusone.rules.gl.shader :as shader]
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
   [minusone.rules.anime.anime :as anime]
   [thi.ng.geom.matrix :as mat]))

(def pos+weights+joints-vert
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

(defn init-fn [world game]
  (-> world
      (esse ::joints-shader #::shader{:program-data (shader/create-program game pos+weights+joints-vert white-frag)})
      (esse ::simpleskin
            #::assimp{:model-to-load ["assets/simpleskin.gltf"] :tex-unit-offset 3}
            #::shader{:use ::joints-shader})
      (esse ::position-shader #::shader{:program-data (shader/create-program game position-vert white-frag)})
      (esse ::simpleanime
            #::assimp{:model-to-load ["assets/simpleanime.gltf"] :tex-unit-offset 21}
            #::shader{:use ::position-shader})))

(defn after-load-fn [world _game]
  (-> world
      (esse ::simpleskin #::t3d{:position (v/vec3 -5.5 0 0.0)})
      (esse ::simpleanime #::t3d{:position (v/vec3 5.5 0 0.0)})))

(def rules
  (o/ruleset
   {::model-w-joints
    [:what
     [esse-id ::gltf/data gltf-data {:then false}]
     [esse-id ::gltf/primitives primitives {:then false}]
     [esse-id ::gltf/transform-db transform-db {:then false}]
     [esse-id ::gltf/inv-bind-mats inv-bind-mats {:then false}]
     [esse-id ::t3d/position position]
     [esse-id ::shader/use shader-id]
     [shader-id ::shader/program-data program-data]
     [::world/global ::projection/matrix projection]
     [::firstperson/player ::firstperson/look-at look-at {:then false}]
     [::firstperson/player ::firstperson/position cam-position {:then false}]
     :then
     (println esse-id "all set!")]}))

(defn render-fn [world game]
  (doseq [{:keys [esse-id primitives position transform-db inv-bind-mats] :as esse}
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
      (when (= (:esse-id esse) ::simpleanime)
        #_{:clj-kondo/ignore [:inline-def]}
        (def hmm gltf-data))
      (gl game useProgram program)
      (gl game uniformMatrix4fv u_view false view)
      (gl game uniformMatrix4fv u_projection false project)
      (when (and joint-mats (> (.-length joint-mats) 0))
        (gl game uniformMatrix4fv u_joint_mats false joint-mats))
      (doseq [prim primitives]
        (let [vao (get @vao/db* (:vao-name prim))]
          (when vao
            (let [indices      (get accessors (:indices prim))
                  node-0-trans (:global-transform (get transform-db 0)) 
                  trans-mat    (m-ext/translation-mat position)
                  rot-mat      (g/as-matrix (q/quat-from-axis-angle
                                             (v/vec3 1.0 0.0 0.0)
                                             (m/radians 0.0)))
                  scale-mat    (m-ext/scaling-mat 8.0)

                  model        (reduce m/* [trans-mat rot-mat scale-mat node-0-trans])
                  model        (f32-arr (vec model))]
              (gl game bindVertexArray vao)
              (gl game uniformMatrix4fv u_model false model)

              (gl game drawElements GL_TRIANGLES (:count indices) (:componentType indices) 0)
              (gl game bindVertexArray #?(:clj 0 :cljs nil)))))))))

(def system
  {::world/init-fn init-fn
   ::world/after-load-fn after-load-fn
   ::world/render-fn render-fn
   ::world/rules rules})

(comment

  hmm

  :-)