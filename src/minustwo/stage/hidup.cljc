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
   [minustwo.gl.constants :refer [GL_BACK GL_CULL_FACE GL_DYNAMIC_DRAW
                                  GL_FRONT GL_TEXTURE0 GL_TEXTURE_2D
                                  GL_TRIANGLES GL_UNIFORM_BUFFER]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.model.assimp :as assimp]
   [minustwo.systems.time :as time]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.view.firstperson :as firstperson]
   [minustwo.systems.view.room :as room]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(def MAX_JOINTS 500)

(def pmx-vert
  (str cljgl/version-str "
  precision mediump float;
  uniform float u_invertedHull;
  uniform float u_lineThickness;
  uniform mat4 u_model;
  uniform mat4 u_view;
  uniform mat4 u_projection;
  layout(std140) uniform Skinning {
     mat4[" MAX_JOINTS "] u_joint_mats;
  };
  
  in vec3 POSITION;
  in vec3 NORMAL;
  in vec2 TEXCOORD_0;
  in vec4 WEIGHTS_0;
  in uvec4 JOINTS_0;
  out vec3 Normal;
  out vec2 TexCoords;
  void main()
  {
    vec4 pos;
    if (u_invertedHull > 0.5) {
       pos = vec4((POSITION + NORMAL * u_lineThickness), 1.0);
    } else {
       pos = vec4(POSITION, 1.0);
    }
    mat4 skin_mat = ((WEIGHTS_0.x * u_joint_mats[JOINTS_0.x]) + (WEIGHTS_0.y * u_joint_mats[JOINTS_0.y]) + (WEIGHTS_0.z * u_joint_mats[JOINTS_0.z]) + (WEIGHTS_0.w * u_joint_mats[JOINTS_0.w]));
    vec4 world_pos = (u_model * skin_mat * pos);
    vec4 cam_pos = (u_view * world_pos);
    gl_Position = (u_projection * cam_pos);
    Normal = NORMAL;
    TexCoords = TEXCOORD_0;
  }"))

(def pmx-frag
  (str cljgl/version-str "
  precision mediump float;
  uniform sampler2D u_mat_diffuse;
  uniform float u_invertedHull;
  in vec3 Normal;
  in vec2 TexCoords;
  out vec4 o_color;
  
  void main() 
  {
      vec4 result;
      if (u_invertedHull > 0.5) {
         vec3 color = vec3(0.0, 0.0, 0.0);
         result = vec4(color, 1.0);
      } else {
         result = vec4(texture(u_mat_diffuse, TexCoords).rgb, 1.0);
      }
      o_color = result;
  }"))

;; this part needs hammock later
(s/def ::custom-draw-fn (s/or :keyword #{:normal-draw}
                              :draw-fn fn?))

(def normal-draw {::custom-draw-fn :normal-draw})

(defn init-fn [world game]
  (println "[minustwo.stage.hidup] system running!")
  (let [ctx (gl-ctx game)]
    (-> world
        (esse :skinning-ubo
              (let [ubo (cljgl/create-buffer ctx)]
                (gl ctx bindBuffer GL_UNIFORM_BUFFER ubo)
                (gl ctx bufferData GL_UNIFORM_BUFFER (* MAX_JOINTS 16 4) GL_DYNAMIC_DRAW)
                (gl ctx bindBufferBase GL_UNIFORM_BUFFER 0 ubo)
                (gl ctx bindBuffer GL_UNIFORM_BUFFER #?(:clj 0 :cljs nil))
                {::shader/ubo ubo}))
        (firstperson/insert-player (v/vec3 0.0 15.5 13.0) (v/vec3 0.0 0.0 -1.0))
        #_(esse ::simpleanime
                #::assimp{:model-to-load ["assets/simpleanime.gltf"]
                          :config {:tex-unit-offset 0}}
                #::shader{:program-info (cljgl/create-program-info-from-iglu ctx vert frag)
                          :use ::simpleanime}
                t3d/default)
        (esse ::pmx-shader #::shader{:program-info (cljgl/create-program-info-from-source ctx pmx-vert pmx-frag)})
        (esse ::rubahperak
              #::assimp{:model-to-load ["assets/models/SilverWolf/SilverWolf.pmx"]
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
        #_(esse ::joints-shader #::shader{:program-info (cljgl/create-program-info-from-iglu ctx  pos+skins-vert white-frag)})
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
            #::t3d{:translation (v/vec3 0.0 0.0 0.0)
                   :scale (v/vec3 1.0)})
      (esse ::rubah
            #::t3d{:translation (v/vec3 -30.0 0.0 0.0)
                   :scale (v/vec3 0.2)})))

(def rules
  (o/ruleset
   {::gltf-models
    [:what
     [:skinning-ubo ::shader/ubo skinning-ubo]
     [esse-id ::gl-magic/casted? true]
     [esse-id ::t3d/transform model]
     [esse-id ::shader/use ::pmx-shader]
     [::pmx-shader ::shader/program-info program-info]
     [esse-id ::gltf/primitives gltf-primitives {:then false}]
     [esse-id ::gltf/joints joints]
     [esse-id ::gltf/inv-bind-mats inv-bind-mats]
     [esse-id ::pose/pose-tree pose-tree]
     [esse-id ::custom-draw-fn draw-fn]
     :then
     (insert! esse-id
              ::gltf/primitives (into [] (map-indexed (fn [idx prim] (assoc prim :idx idx))) gltf-primitives))]

    ::update-anime
    [:what
     [::time/now ::time/slice 2]
     [esse-id ::pose/pose-tree pose-tree]
     [::world/global ::anime/db* anime-db*]
     :then
     (let [anime     (get (::anime/interpolated @anime-db*) esse-id)
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
     (let [global-tt (into [] gltf/global-transform-xf pose-tree)]
       (insert! esse-id ::pose/pose-tree global-tt))]}))

(defn render-fn [world _game]
  (let [{:keys [ctx project player-view vao-db* texture-db*]}
        (utils/query-one world ::room/data)]
    (when-let [gltf-models (seq (o/query-all world ::gltf-models))]
      (doseq [gltf-model gltf-models]
        (let [{:keys [draw-fn model program-info joints pose-tree inv-bind-mats skinning-ubo]} gltf-model
              joint-mats (gltf/create-joint-mats-arr joints pose-tree inv-bind-mats)
              node-0     (some-> (get pose-tree 0) :global-transform)
              model      (when node-0 (m/* node-0 model) model)]

          (gl ctx useProgram (:program program-info))
          (cljgl/set-uniform ctx program-info 'u_projection (vec->f32-arr (vec project)))
          (cljgl/set-uniform ctx program-info 'u_view (vec->f32-arr (vec player-view)))
          (cljgl/set-uniform ctx program-info 'u_model (vec->f32-arr (vec model)))

          (gl ctx bindBuffer GL_UNIFORM_BUFFER skinning-ubo)
          (gl ctx bufferSubData GL_UNIFORM_BUFFER 0 joint-mats)
          (gl ctx bindBuffer GL_UNIFORM_BUFFER #?(:clj 0 :cljs nil))

          (doseq [prim (:gltf-primitives gltf-model)]
            (let [indices        (:indices prim)
                  count          (:count indices)
                  component-type (:componentType indices)
                  vao            (get @vao-db* (:vao-name prim))
                  tex            (get @texture-db* (:tex-name prim))]
              (when vao
                (gl ctx bindVertexArray vao)
                (when-let [{:keys [tex-unit texture]} tex]
                  (gl ctx activeTexture (+ GL_TEXTURE0 tex-unit))
                  (gl ctx bindTexture GL_TEXTURE_2D texture)
                  (cljgl/set-uniform ctx program-info 'u_mat_diffuse tex-unit))

                (if (#{20 21} (:idx prim))
                  (gl ctx disable GL_CULL_FACE)
                  (gl ctx enable GL_CULL_FACE))

                (when (#{1 2 9 10 11 12 13 14 15 16 17 18 20 22 23} (:idx prim))

                  (cljgl/set-uniform ctx program-info 'u_invertedHull 1.0)
                  (cljgl/set-uniform ctx program-info 'u_lineThickness 0.03)
                  (gl ctx cullFace GL_FRONT)
                  (condp = draw-fn
                    :normal-draw
                    (gl ctx drawElements GL_TRIANGLES count component-type 0)

                    (draw-fn ctx gltf-model prim))
                  (gl ctx cullFace GL_BACK))

                (cljgl/set-uniform ctx program-info 'u_invertedHull 0.0)
                (condp = draw-fn
                  :normal-draw
                  (gl ctx drawElements GL_TRIANGLES count component-type 0)

                  (draw-fn ctx gltf-model prim))

                (gl ctx bindVertexArray #?(:clj 0 :cljs nil))))))))))

(def system
  {::world/init-fn init-fn
   ::world/after-load-fn after-load-fn
   ::world/rules rules
   ::world/render-fn render-fn})
