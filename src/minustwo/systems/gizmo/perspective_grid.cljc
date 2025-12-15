(ns minustwo.systems.gizmo.perspective-grid
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.sugar :refer [f32-arr i32-arr]]
   [engine.utils :as utils]
   [engine.world :as world :refer [esse]]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_DEPTH_TEST
                                  GL_ELEMENT_ARRAY_BUFFER GL_FLOAT
                                  GL_TRIANGLES GL_UNSIGNED_INT]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.gl.vao :as vao]
   [minustwo.systems.view.room :as room]
   [odoyle.rules :as o]
   [thi.ng.math.core :as m]))

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
}"})

(def ^floats quad
  (f32-arr
   [-1.0 -1.0
    1.0 -1.0
    1.0  1.0
    -1.0 1.0]))

(def ^ints quad-indices
  (i32-arr [0 1 2 0 2 3]))

(defn init-fn [world game]
  (let [ctx (:webgl-context game)]
    (-> world
        (esse ::grid
              #::shader{:program-info (cljgl/create-program-info ctx perspective-vert perspective-frag)
                        :use ::grid}
              #::gl-magic{:spell [{:bind-vao ::grid}
                                  {:buffer-data quad :buffer-type GL_ARRAY_BUFFER}
                                  {:point-attr 'a_pos :use-shader ::grid :count (gltf/gltf-type->num-of-component "VEC2") :component-type GL_FLOAT}
                                  {:buffer-data quad-indices :buffer-type GL_ELEMENT_ARRAY_BUFFER}
                                  {:unbind-vao true}]}))))

(def rules
  (o/ruleset
   {::grid-model
    [:what
     [::grid ::gl-magic/casted? true]
     [::grid ::shader/program-info grid-prog]]}))

(defn render-fn [world _game]
  (let [room-data (utils/query-one world ::room/data)
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
          (gl enable GL_DEPTH_TEST))))))

(def system
  {::world/init-fn init-fn
   ::world/rules rules
   ::world/render-fn render-fn})