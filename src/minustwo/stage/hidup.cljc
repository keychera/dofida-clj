(ns minustwo.stage.hidup
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [com.rpl.specter :as sp]
   [engine.sugar :refer [f32-arr i32-arr]]
   [engine.world :as world :refer [esse]]
   [minustwo.gl.vao :as vao]
   [minustwo.anime.anime :as anime]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_DEPTH_TEST
                                  GL_ELEMENT_ARRAY_BUFFER GL_FLOAT GL_TEXTURE0
                                  GL_TEXTURE_2D GL_TRIANGLES GL_UNSIGNED_INT]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.gl.texture :as texture]
   [minustwo.model.assimp :as assimp]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.view.firstperson :as firstperson]
   [minustwo.systems.view.projection :as projection]
   [engine.utils :as utils]
   [odoyle.rules :as o]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(def vert
  {:precision  "mediump float"
   :inputs     '{POSITION vec3}
   :uniforms   '{u_model      mat4
                 u_view       mat4
                 u_projection mat4}
   :signatures '{main ([] void)}
   :functions  '{main ([]
                       (=vec4 pos (vec4 POSITION "1.0"))
                       (= gl_Position (* u_projection u_view u_model pos)))}})

(def frag
  {:precision  "mediump float"
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions  '{main ([] (= o_color (vec4 "0.2" "0.2" "0.2" "1.0")))}})

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

(def white-frag
  {:precision  "mediump float"
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions  '{main ([] (= o_color (vec4 "1.0")))}})


;; https://gist.github.com/bgolus/3a561077c86b5bfead0d6cc521097bae
(def perspective-vert
  {:precision  "mediump float"
   :inputs     '{a_pos vec2}
   :outputs    '{v_world_dir vec3}
   :uniforms   '{u_inv_proj mat4
                 u_inv_view mat4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (=vec4 pos (vec4 a_pos.x a_pos.y "0.0" "1.0"))
           (=vec4 view_dir (* u_inv_proj pos))
           (=vec4 world_dir (* u_inv_view (vec4 view_dir.xyz "0.0")))
           (= v_world_dir (normalize world_dir.xyz))
           (= gl_Position pos))}})

(def perspective-frag
  {:precision  "mediump float"
   :inputs     '{v_world_dir vec3}
   :outputs    '{o_color vec4}
   :uniforms   '{u_cam_pos vec3}
   :functions
   ;; Pristine grid from The Best Darn Grid Shader (yet)
   ;; https://bgolus.medium.com/the-best-darn-grid-shader-yet-727f9278b9d8
   "// grid-shader
const float N = 32.0;
float pristineGrid( in vec2 uv, vec2 lineWidth) {
    vec2 ddx = dFdx(uv);
    vec2 ddy = dFdy(uv);
    vec2 uvDeriv = vec2(length(vec2(ddx.x, ddy.x)), length(vec2(ddx.y, ddy.y)));
    bvec2 invertLine = bvec2(lineWidth.x > 0.5, lineWidth.y > 0.5);
    vec2 targetWidth = vec2(
      invertLine.x ? 1.0 - lineWidth.x : lineWidth.x,
      invertLine.y ? 1.0 - lineWidth.y : lineWidth.y
      );
    vec2 drawWidth = clamp(targetWidth, uvDeriv, vec2(0.5));
    vec2 lineAA = uvDeriv * 1.5;
    vec2 gridUV = abs(fract(uv) * 2.0 - 1.0);
    gridUV.x = invertLine.x ? gridUV.x : 1.0 - gridUV.x;
    gridUV.y = invertLine.y ? gridUV.y : 1.0 - gridUV.y;
    vec2 grid2 = smoothstep(drawWidth + lineAA, drawWidth - lineAA, gridUV);

    grid2 *= clamp(targetWidth / drawWidth, 0.0, 1.0);
    grid2 = mix(grid2, targetWidth, clamp(uvDeriv * 2.0 - 1.0, 0.0, 1.0));
    grid2.x = invertLine.x ? 1.0 - grid2.x : grid2.x;
    grid2.y = invertLine.y ? 1.0 - grid2.y : grid2.y;
    return mix(grid2.x, 1.0, grid2.y);
}

void main() {
  vec3  ray = v_world_dir;
  vec3  cam = u_cam_pos;  
  if (ray.y >= 0.0 && cam.y > 0.0 || ray.y <= 0.0 && cam.y < 0.0) {
     discard;
  }

  // if ray.y is almost zero, ray is nearly parallel to plane
  float   t = -cam.y / ray.y;
  vec3  hit = cam + ray * t;
    
  vec2   uv = hit.xz;
  float   g = pristineGrid( uv * 0.25, vec2(1.0/N) );
  o_color   = vec4(vec3(1.0), g * 0.3);
}
    
    "})

(def ^floats quad
  (f32-arr
   [-1.0 -1.0
    1.0 -1.0
    1.0  1.0
    -1.0 1.0]))

(def ^ints quad-indices
  (i32-arr [0 1 2 0 2 3]))

(defn init-fn [world game]
  (println "[minustwo.stage.hidup] system running!")
  (let [ctx (:webgl-context game)]
    (-> world
        (firstperson/insert-player (v/vec3 0.0 18.0 72.0) (v/vec3 0.0 0.0 -1.0))
        (esse ::grid
              #::shader{:program-info (cljgl/create-program-info ctx perspective-vert perspective-frag)
                        :use ::grid}
              #::gl-magic{:spell [{:bind-vao ::grid}
                                  {:buffer-data quad :buffer-type GL_ARRAY_BUFFER}
                                  {:point-attr 'a_pos :use-shader ::grid :count (gltf/gltf-type->num-of-component "VEC2") :component-type GL_FLOAT}
                                  {:buffer-data quad-indices :buffer-type GL_ELEMENT_ARRAY_BUFFER}
                                  {:unbind-vao true}]})
        (esse ::simpleanime
              #::assimp{:model-to-load ["assets/simpleanime.gltf"] :tex-unit-offset 0}
              #::shader{:program-info (cljgl/create-program-info ctx vert frag)
                        :use ::simpleanime}
              t3d/default)
        (esse ::pmx-shader #::shader{:program-info (cljgl/create-program-info ctx pmx-vert pmx-frag)})
        (esse ::rubahperak
              #::assimp{:model-to-load ["assets/models/SilverWolf/银狼.pmx"] :tex-unit-offset 2}
              #::shader{:use ::pmx-shader}
              t3d/default)
        (esse ::rubah
              #::assimp{:model-to-load ["assets/fox.glb"] :tex-unit-offset 10}
              #::shader{:use ::pmx-shader}
              t3d/default)
        (esse ::joints-shader #::shader{:program-info (cljgl/create-program-info ctx  pos+skins-vert white-frag)})
        (esse ::simpleskin
              #::assimp{:model-to-load ["assets/simpleskin.gltf"] :tex-unit-offset 20}
              #::shader{:use ::joints-shader}
              t3d/default))))

(defn after-load-fn [world _game]
  (-> world
      (esse ::simpleanime
            #::t3d{:translation (v/vec3 0.0 0.0 0.0)
                   :scale (v/vec3 8.0)})
      (esse ::simpleskin
            #::t3d{:translation (v/vec3 0.0 0.0 -16.0)
                   :scale (v/vec3 24.0)})
      (esse ::rubahperak
            #::t3d{:translation (v/vec3 30.0 0.0 0.0)})
      (esse ::rubah
            #::t3d{:translation (v/vec3 -30.0 0.0 0.0)
                   :scale (v/vec3 0.2)})))

(def rules
  (o/ruleset
   {::room-data
    [:what
     [::world/global ::gl-system/context ctx]
     [::world/global ::projection/matrix project]
     [::firstperson/player ::firstperson/look-at player-view]
     [::firstperson/player ::firstperson/position player-pos]]

    ::grid-model
    [:what
     [::grid ::gl-magic/casted? true]
     [::grid ::shader/program-info grid-prog]]

    ::gltf-models
    [:what
     [esse-id ::gl-magic/casted? true]
     [esse-id ::t3d/transform model]
     [esse-id ::shader/use shader-id]
     [shader-id ::shader/program-info program-info]
     [esse-id ::gltf/primitives gltf-primitives]
     [esse-id ::gltf/joints joints]
     [esse-id ::gltf/transform-tree transform-tree]
     [esse-id ::gltf/inv-bind-mats inv-bind-mats]]}))

(defn render-fn [world game]
  (let [room-data (utils/query-one world ::room-data)
        ctx       (:ctx room-data)
        project   (:project room-data)
        view      (:player-view room-data)
        view-pos  (:player-pos room-data)]
    (when-let [render-data (utils/query-one world ::grid-model)]
      (let [inv-proj   (m/invert project)
            inv-view   (m/invert view)
            grid-prog  (:grid-prog render-data)
            grid-vao   (get @vao/db* ::grid)]
        (doto ctx
          (gl useProgram (:program grid-prog))
          (gl bindVertexArray grid-vao)

          (cljgl/set-uniform grid-prog 'u_inv_view (f32-arr (vec inv-view)))
          (cljgl/set-uniform grid-prog 'u_inv_proj (f32-arr (vec inv-proj)))
          (cljgl/set-uniform grid-prog 'u_cam_pos (f32-arr (into [] view-pos)))

          (gl disable GL_DEPTH_TEST)
          (gl drawElements GL_TRIANGLES 6 GL_UNSIGNED_INT 0)
          (gl enable GL_DEPTH_TEST))))

    (when-let [gltf-models (o/query-all world ::gltf-models)]
      (doseq [gltf-model gltf-models]
        (let [{:keys [esse-id model program-info joints transform-tree inv-bind-mats]} gltf-model
              time  (:total-time game)
              anime (get (::anime/interpolated @anime/db*) esse-id)
              transform-tree (into []
                                   (map (fn [{:keys [idx] :as node}]
                                          (let [value            (get anime idx)
                                                next-translation (get value :translation)
                                                next-rotation    (get value :rotation)
                                                next-scale       (get value :scale)]
                                            (cond-> node
                                              next-translation (assoc :translation next-translation)
                                              next-rotation (assoc :rotation next-rotation)
                                              next-scale (assoc :scale next-scale)))))
                                   transform-tree)
              transform-tree (if (= esse-id ::rubahperak)
                               (->> transform-tree
                                    (sp/transform [sp/ALL #(#{"左腕" "右腕"} (:name %)) :translation]
                                                  (fn [gt] (->> gt (m/+ (v/vec3 0.0 0.0 0.0)))))
                                    (sp/transform [sp/ALL #(#{"上半身" "首"} (:name %)) :rotation]
                                                  (fn [_] (q/quat-from-axis-angle
                                                           (v/vec3 0.0 1.0 0.0)
                                                           (m/radians (* 10 (Math/sin (* 0.005 time))))))))
                               transform-tree)
              joint-mats (gltf/create-joint-mats-arr joints transform-tree inv-bind-mats)
              ;; duplicated calc here because gltf/create-joint-mats-arr also does this but it isn't returned
              ;; will hammock node-0 more later because it clashes with our t3d/transform
              node-0     (some-> (get transform-tree 0) (gltf/calc-local-transform) :local-transform)
              model      (m/* node-0 model)]

          (gl ctx useProgram (:program program-info))
          (cljgl/set-uniform ctx program-info 'u_projection (f32-arr (vec project)))
          (cljgl/set-uniform ctx program-info 'u_view (f32-arr (vec view)))
          (cljgl/set-uniform ctx program-info 'u_model (f32-arr (vec model)))
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

                (gl ctx drawElements GL_TRIANGLES count component-type 0)
                (gl ctx bindVertexArray nil)))))))))

(def system
  {::world/init-fn init-fn
   ::world/after-load-fn after-load-fn
   ::world/rules rules
   ::world/render-fn render-fn})
