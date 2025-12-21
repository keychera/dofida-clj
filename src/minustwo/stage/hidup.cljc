(ns minustwo.stage.hidup
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.spec.alpha :as s]
   [engine.game :refer [gl-ctx]]
   [engine.macros :refer [insert!]]
   [engine.math :as m-ext]
   [engine.sugar :refer [vec->f32-arr]]
   [engine.utils :as utils]
   [engine.world :as world :refer [esse]]
   [minustwo.anime.anime :as anime]
   [minustwo.anime.pose :as pose]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_TEXTURE0 GL_TEXTURE_2D GL_TRIANGLES]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.gl.texture :as texture]
   [minustwo.gl.vao :as vao]
   [minustwo.model.assimp :as assimp]
   [minustwo.systems.time :as time]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.view.firstperson :as firstperson]
   [minustwo.systems.view.room :as room]
   [odoyle.rules :as o]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.core :as g]))

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

;; this part needs hammock later
(s/def ::custom-draw-fn (s/or :keyword #{:normal-draw}
                              :draw-fn fn?))

(def normal-draw {::custom-draw-fn :normal-draw})

(defn init-fn [world game]
  (println "[minustwo.stage.hidup] system running!")
  (let [ctx (gl-ctx game)]
    (-> world
        (firstperson/insert-player (v/vec3 0.0 15.5 13.0) (v/vec3 0.0 0.0 -1.0))
        #_(esse ::simpleanime
                #::assimp{:model-to-load ["assets/simpleanime.gltf"] :tex-unit-offset 0}
                #::shader{:program-info (cljgl/create-program-info ctx vert frag)
                          :use ::simpleanime}
                t3d/default)
        (esse ::pmx-shader #::shader{:program-info (cljgl/create-program-info ctx pmx-vert pmx-frag)})
        (esse ::rubahperak
              #::assimp{:model-to-load ["assets/models/SilverWolf/银狼.pmx"] :tex-unit-offset 2}
              #::shader{:use ::pmx-shader}
              pose/default
              normal-draw
              t3d/default)
        #_(esse ::rubah
                #::assimp{:model-to-load ["assets/fox.glb"] :tex-unit-offset 10}
                #::shader{:use ::pmx-shader}
                pose/default
                normal-draw
                t3d/default)
        #_(esse ::joints-shader #::shader{:program-info (cljgl/create-program-info ctx  pos+skins-vert white-frag)})
        #_(esse ::simpleskin
                #::assimp{:model-to-load ["assets/simpleskin.gltf"] :tex-unit-offset 20}
                #::shader{:use ::joints-shader}
                t3d/default))))

;; https://stackoverflow.com/a/11741520/8812880
(defn orthogonal [[x y z :as v]]
  (let [x (Math/abs x)
        y (Math/abs y)
        z (Math/abs z)
        other (if (< x y)
                (if (< x z)
                  (v/vec3 1.0 0.0 0.0)
                  (v/vec3 0.0 0.0 1.0))
                (if (< y z)
                  (v/vec3 0.0 1.0 0.0)
                  (v/vec3 0.0 0.0 1.0)))]
    (m/cross v other)))

(defn rotatation-of-u->v [u v]
  (let [k-cosθ (m/dot u v)
        k      (Math/sqrt (* (m/mag-squared u) (m/mag-squared v)))]
    (if (= (/ k-cosθ k) -1.0)
      (q/quat (m/normalize (orthogonal u)) 0.0)
      (m/normalize (q/quat (m/cross u v) (+ k-cosθ k))))))

(defn clamped-acos [cos-v]
  (cond
    (<= cos-v -1) Math/PI
    (>= cos-v 1) 0.0
    :else (Math/acos cos-v)))

;; man Idk if this is entirely correct
(defn solve-IK1 [root-t mid-t end-t target-t]
  (let [origin-v     (m/- end-t root-t)
        target-v     (m/- (m/+ target-t end-t) root-t)
        root-angle   (rotatation-of-u->v origin-v target-v)
        [axis angle] (q/as-axis-angle root-angle)

        target-r     (g/rotate-around-axis target-t axis angle)
        AB           (m/mag (m/+ root-t mid-t))
        BC           (m/mag (m/+ mid-t end-t))
        AC           (m/mag (m/+ root-t target-r))
        cos-alpha    (/ (+ (* AB AB) (* AC AC) (* -1.0 BC BC))
                        (* 2.0 AB AC))
        alpha        (clamped-acos cos-alpha)
        cos-beta     (/ (+ (* AB AB) (* BC BC) (* -1.0 AC AC))
                        (* 2.0 AB BC))
        beta         (clamped-acos cos-beta)
        root-IK      (q/quat-from-axis-angle axis (- alpha))
        mid-IK       (q/quat-from-axis-angle axis (- Math/PI beta))]
    [(m/* root-angle root-IK) mid-IK]))

(defn IK-transducer [a b c target]
  (fn [rf]
    (let [fa (volatile! nil)
          fb (volatile! nil)
          fc (volatile! nil)]
      (fn
        ([] (rf))
        ([result]
         (let [root-t     (m-ext/m44->trans-vec3
                           (m/* (:parent-transform @fa)
                                (m-ext/vec3->trans-mat (:translation @fa))))
               mid-t      (m-ext/m44->trans-vec3
                           (m/* (:parent-transform @fb)
                                (m-ext/vec3->trans-mat (:translation @fb))))
               end-t      (m-ext/m44->trans-vec3
                           (m/* (:parent-transform @fc)
                                (m-ext/vec3->trans-mat (:translation @fc))))
               [root-IK mid-IK] (solve-IK1 root-t mid-t end-t target)
               result (-> result
                          (assoc! (:idx @fa) (update @fa :rotation m/* root-IK))
                          (assoc! (:idx @fb) (update @fb :rotation m/* mid-IK)))]
           (rf result)))
        ([result input]
         (cond
           (= a (:name input)) (vreset! fa input)
           (= b (:name input)) (vreset! fb input)
           (= c (:name input)) (vreset! fc input))
         (rf result input))))))

(def poseA
  (comp
   gltf/global-transform-xf
   ;; absolute cinema
   (IK-transducer "左腕" "左ひじ" "左手首" (v/vec3 4.27 3.0 10.0))
   (IK-transducer "右腕" "右ひじ" "右手首" (v/vec3 -4.27 3.0 10.0))))

(defn after-load-fn [world _game]
  (-> world
      (esse ::simpleanime
            #::t3d{:translation (v/vec3 0.0 0.0 0.0)
                   :scale (v/vec3 8.0)})
      (esse ::simpleskin
            #::t3d{:translation (v/vec3 0.0 0.0 -16.0)
                   :scale (v/vec3 24.0)})
      (esse ::rubahperak
            (pose/strike poseA)
            #::t3d{:translation (v/vec3 0.0 0.0 0.0)})
      (esse ::rubah
            #::t3d{:translation (v/vec3 -30.0 0.0 0.0)
                   :scale (v/vec3 0.2)})))

(def rules
  (o/ruleset
   {::gltf-models
    [:what
     [esse-id ::gl-magic/casted? true]
     [esse-id ::t3d/transform model]
     [esse-id ::shader/use shader-id]
     [shader-id ::shader/program-info program-info]
     [esse-id ::gltf/primitives gltf-primitives]
     [esse-id ::gltf/joints joints]
     [esse-id ::gltf/inv-bind-mats inv-bind-mats]
     [esse-id ::pose/pose-tree pose-tree]
     [esse-id ::custom-draw-fn draw-fn]]

    ::update-anime
    [:what
     [::time/now ::time/slice 2]
     [esse-id ::pose/pose-tree pose-tree]
     :then
     (let [anime     (get (::anime/interpolated @anime/db*) esse-id)
           pose-tree (if anime
                       (into []
                             (map (fn [{:keys [idx]
                                        :as   node}]
                                    (let [value            (get anime idx)
                                          next-translation (get value :translation)
                                          next-rotation    (get value :rotation)
                                          next-scale       (get value :scale)]
                                      (cond-> node
                                        next-translation (assoc :translation next-translation)
                                        next-rotation (assoc :rotation next-rotation)
                                        next-scale (assoc :scale next-scale)))))
                             pose-tree)
                       pose-tree)]
       (insert! esse-id ::pose/pose-tree pose-tree))]

    ::global-transform
    [:what
     [::time/now ::time/total tt {:then false}]
     [::time/now ::time/slice 4]
     [esse-id ::pose/pose-tree pose-tree {:then false}]
     :then
     (let [pose-tree (into []
                           #_(map (fn [{:keys [name rotation original-r] :as node}]
                                    (if-let [bone-pose (get {"左腕" [(v/vec3 0.0 -0.0 0.0) 200.0 180.0]} name)]
                                      (let [[axis-angle offset ampl] bone-pose
                                            next-rotation (q/quat-from-axis-angle
                                                           axis-angle
                                                           (m/radians (* ampl (Math/sin (/ (+ tt offset) 640.0)))))]
                                        (cond-> node
                                          (nil? original-r) (assoc :original-r rotation)
                                          next-rotation (assoc :rotation (m/* (or  original-r rotation) next-rotation))))
                                      node)))
                           pose-tree)
           global-tt (into [] gltf/global-transform-xf pose-tree)]
       (insert! esse-id ::pose/pose-tree global-tt))]}))

(defn render-fn [world _game]
  (let [room-data (utils/query-one world ::room/data)
        ctx       (:ctx room-data)
        project   (:project room-data)
        view      (:player-view room-data)]
    (when-let [gltf-models (seq (o/query-all world ::gltf-models))]
      (doseq [gltf-model gltf-models]
        (let [{:keys [draw-fn model program-info joints pose-tree inv-bind-mats]} gltf-model
              joint-mats (gltf/create-joint-mats-arr joints pose-tree inv-bind-mats)
              node-0     (some-> (get pose-tree 0) :global-transform)
              model      (when node-0 (m/* node-0 model) model)]

          (gl ctx useProgram (:program program-info))
          (cljgl/set-uniform ctx program-info 'u_projection (vec->f32-arr (vec project)))
          (cljgl/set-uniform ctx program-info 'u_view (vec->f32-arr (vec view)))
          (cljgl/set-uniform ctx program-info 'u_model (vec->f32-arr (vec model)))
          (when-let [{:keys [uni-loc]} (get (:uni-locs program-info) 'u_joint_mats)]
            (gl ctx uniformMatrix4fv uni-loc false joint-mats))

          (doseq [prim (:gltf-primitives gltf-model)]
            (let [indices        (:indices prim)
                  count          (:count indices)
                  component-type (:componentType indices)
                  vao            (get @vao/db* (:vao-name prim))
                  tex            (get @texture/db* (:tex-name prim))]
              (when vao
                (gl ctx bindVertexArray vao)
                (when-let [{:keys [tex-unit texture]} tex]
                  (gl ctx activeTexture (+ GL_TEXTURE0 tex-unit))
                  (gl ctx bindTexture GL_TEXTURE_2D texture)
                  (cljgl/set-uniform ctx program-info 'u_mat_diffuse tex-unit))

                (condp = draw-fn
                  :normal-draw
                  (gl ctx drawElements GL_TRIANGLES count component-type 0)

                  (draw-fn ctx gltf-model prim))

                (gl ctx bindVertexArray nil)))))))))

(def system
  {::world/init-fn init-fn
   ::world/after-load-fn after-load-fn
   ::world/rules rules
   ::world/render-fn render-fn})
