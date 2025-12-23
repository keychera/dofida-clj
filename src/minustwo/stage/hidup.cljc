(ns minustwo.stage.hidup
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.spec.alpha :as s]
   [engine.game :refer [gl-ctx]]
   [engine.macros :refer [insert!]]
   [engine.sugar :refer [vec->f32-arr]]
   [engine.utils :as utils]
   [engine.world :as world :refer [esse]]
   [minustwo.anime.anime :as anime]
   [minustwo.anime.IK :refer [IK-transducer1]]
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
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

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
                 u_joint_mats [mat4 254]}
   :raw ;; currently with raw, we need to rewrite again the above definitions (need hammock time for this)
   (str cljgl/version-str
        "
precision mediump float;
uniform mat4 u_model;
uniform mat4 u_view;
uniform mat4 u_projection;
uniform mat4[500] u_joint_mats;

in vec3 POSITION;
in vec3 NORMAL;
in vec2 TEXCOORD_0;
in vec4 WEIGHTS_0;
in uvec4 JOINTS_0;
out vec3 Normal;
out vec2 TexCoords;
void main()
{
  vec4 pos = (vec4(POSITION, 1.0));
  mat4 skin_mat = ((WEIGHTS_0.x * u_joint_mats[JOINTS_0.x]) + (WEIGHTS_0.y * u_joint_mats[JOINTS_0.y]) + (WEIGHTS_0.z * u_joint_mats[JOINTS_0.z]) + (WEIGHTS_0.w * u_joint_mats[JOINTS_0.w]));
  vec4 world_pos = (u_model * skin_mat * pos);
  vec4 cam_pos = (u_view * world_pos);
  gl_Position = (u_projection * cam_pos);
  Normal = NORMAL;
  TexCoords = TEXCOORD_0;
}")})

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
                #::assimp{:model-to-load ["assets/simpleanime.gltf"]
                          :config {:tex-unit-offset 0}}
                #::shader{:program-info (cljgl/create-program-info ctx vert frag)
                          :use ::simpleanime}
                t3d/default)
        (esse ::pmx-shader #::shader{:program-info (cljgl/create-program-info ctx pmx-vert pmx-frag)})
        (esse ::rubahperak
              #::assimp{:model-to-load ["assets/models/SilverWolf/银狼.pmx"]
                        :config {:tex-unit-offset 2}}
              #::shader{:use ::pmx-shader}
              pose/default
              normal-draw
              t3d/default)
        #_(esse ::rubah
                #::assimp{:model-to-load ["assets/fox.glb"] :config {:tex-unit-offset 10}}
                #::shader{:use ::pmx-shader}
                pose/default
                normal-draw
                t3d/default)
        #_(esse ::joints-shader #::shader{:program-info (cljgl/create-program-info ctx  pos+skins-vert white-frag)})
        #_(esse ::simpleskin
                #::assimp{:model-to-load ["assets/simpleskin.gltf"] :config {:tex-unit-offset 20}}
                #::shader{:use ::joints-shader}
                t3d/default))))

(def pose-rest
  (comp
   gltf/global-transform-xf
   (IK-transducer1 "左腕" "左ひじ" "左手首" (v/vec3 10.0 -70.0 0.0))
   (IK-transducer1 "右腕" "右ひじ" "右手首" (v/vec3 -10.0 -70.0 0.0))))

(def absolute-cinema
  (comp
   gltf/global-transform-xf
   (IK-transducer1 "左腕" "左ひじ" "左手首" (v/vec3 4.27 3.0 10.0))
   (IK-transducer1 "右腕" "右ひじ" "右手首" (v/vec3 -4.27 3.0 10.0))))

(defn after-load-fn [world _game]
  (-> world
      (esse ::simpleanime
            #::t3d{:translation (v/vec3 0.0 0.0 0.0)
                   :scale (v/vec3 8.0)})
      (esse ::simpleskin
            #::t3d{:translation (v/vec3 0.0 0.0 -16.0)
                   :scale (v/vec3 24.0)})
      (esse ::rubahperak
            #_(pose/strike pose-rest)
            (pose/anime 20.0
                        [[0.0 pose-rest identity]
                         [0.3 absolute-cinema identity]
                         [15.5 absolute-cinema identity]
                         [16.0 pose-rest identity]])
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
