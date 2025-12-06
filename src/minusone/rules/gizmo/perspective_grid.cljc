(ns minusone.rules.gizmo.perspective-grid
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.sugar :refer [f32-arr i32-arr]]
   [engine.world :as world]
   [minusone.esse :refer [esse]]
   [minusone.rules.gl.gl :as gl]
   [minusone.rules.gl.magic :as gl-magic]
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.vao :as vao]
   [minusone.rules.projection :as projection]
   [minusone.rules.view.firstperson :as firstperson]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

;; https://gist.github.com/bgolus/3a561077c86b5bfead0d6cc521097bae
(def vert
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

(def frag
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
  (-> world
      (esse ::perspective-gizmo
            #::gl-magic{:incantation
                        [{:buffer-data quad :buffer-type (gl game ARRAY_BUFFER)}
                         {:bind-vao ::perspective-gizmo}
                         {:bind-current-buffer true}
                         {:point-attr 'a_pos :use-shader ::perspective-gizmo-shader
                          :attr-size 2 :attr-type (gl game FLOAT)}
                         {:buffer-data quad-indices :buffer-type (gl game ELEMENT_ARRAY_BUFFER)}
                         {:unbind-vao true}]})))

(defn after-load-fn [world game]
  (-> world
      (firstperson/insert-player (v/vec3 0.0 18.0 24.0) (v/vec3 0.0 0.0 -1.0))
      (esse ::perspective-gizmo-shader
            #::shader{:program-data (shader/create-program game vert frag)})))

(def rules
  (o/ruleset
   {::perspective-gizmo
    [:what
     [::perspective-gizmo ::gl/loaded? true]
     [::perspective-gizmo-shader ::shader/program-data program-data]
     [::world/global ::projection/matrix projection]
     [::firstperson/player ::firstperson/look-at look-at {:then false}]
     [::firstperson/player ::firstperson/position cam-pos {:then false}]
     :then
     (println "gizmo go!")]}))

(defn render-fn [world game]
  (when-let [esse (first (o/query-all world ::perspective-gizmo))]
    (let [program-data (:program-data esse)
          program      (:program program-data)
          uni-locs     (:uni-locs program-data)
          u_inv_view   (get uni-locs 'u_inv_view)
          u_inv_proj   (get uni-locs 'u_inv_proj)
          u_cam_pos    (get uni-locs 'u_cam_pos)

          vao          (get @vao/db* ::perspective-gizmo)
          inv-view     (m/invert (:look-at esse))
          inv-project  (m/invert (:projection esse))
          cam-pos      (:cam-pos esse)]

      (gl game useProgram program)
      (gl game bindVertexArray vao)
      (gl game uniformMatrix4fv u_inv_view false (f32-arr (vec inv-view)))
      (gl game uniformMatrix4fv u_inv_proj false (f32-arr (vec inv-project)))
      (gl game uniform3fv u_cam_pos (f32-arr (into [] cam-pos)))

      (gl game disable (gl game DEPTH_TEST))
      (gl game drawElements (gl game TRIANGLES) 6 (gl game UNSIGNED_INT) 0)
      (gl game enable (gl game DEPTH_TEST)))))

(def system
  {::world/init-fn init-fn
   ::world/after-load-fn after-load-fn
   ::world/rules rules
   ::world/render-fn render-fn})