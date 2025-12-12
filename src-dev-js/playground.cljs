(ns playground
  (:require
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr i32-arr]]
   [minusone.rules.gl.gltf :as gltf]
   [minusone.rules.model.assimp-js :as assimp-js]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_COLOR_BUFFER_BIT
                                  GL_DEPTH_BUFFER_BIT GL_DEPTH_TEST
                                  GL_ELEMENT_ARRAY_BUFFER GL_FLOAT
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA
                                  GL_STATIC_DRAW GL_TRIANGLES GL_UNSIGNED_INT]]
   [minustwo.gl.macros :refer [webgl] :rename {webgl gl}]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(defonce canvas (js/document.querySelector "canvas"))
(defonce ctx (.getContext canvas "webgl2" (clj->js {:premultipliedAlpha false})))
(defonce width (-> canvas .-clientWidth))
(defonce height (-> canvas .-clientHeight))

(assimp-js/then-load-model
 ["assets/simpleanime.gltf"]
 #_{:clj-kondo/ignore [:inline-def]}
 (fn [{:keys [gltf bins]}]
   (def gltf-data gltf)
   (def result-bin (first bins))))

(:skins gltf-data)
(:nodes gltf-data)
(take 6 (:accessors gltf-data))
(:bufferViews gltf-data)
(-> gltf-data :meshes first)
(:buffers gltf-data)

(let [accessors    (-> gltf-data :accessors)
      buffer-views (-> gltf-data :bufferViews)
      skins        (-> gltf-data :skins first)
      ibm          (-> skins :inverseBindMatrices)
      accessor     (get accessors ibm)
      buffer-view  (get buffer-views (:bufferView accessor))
      byteLength   (:byteLength buffer-view)
      byteOffset   (:byteOffset buffer-view)
      ibm-uint8s   (.subarray result-bin byteOffset (+ byteLength byteOffset))
      ibm-f32s     (js/Float32Array. ibm-uint8s.buffer
                                     ibm-uint8s.byteOffset
                                     (/ ibm-uint8s.byteLength 4.0))
      nodes        (map-indexed (fn [idx node] (assoc node :idx idx)) (:nodes gltf-data))]

  [(/ (.-length ibm-f32s) 16) :done
   (nth nodes 0)
   (gltf/node->transform-db nodes)])

(defn limited-game-loop
  ([loop-fn how-long]
   (limited-game-loop loop-fn {:total (js/performance.now) :delta 0} how-long))
  ([loop-fn time-data how-long]
   (if (> how-long 0)
     (js/requestAnimationFrame
      (fn [ts]
        (let [delta (- ts (:total time-data))
              time-data (assoc time-data :total ts :delta delta)]
          (loop-fn time-data)
          (limited-game-loop loop-fn time-data (- how-long delta)))))
     (println "done"))))

(def vert
  {:precision  "mediump float"
   :inputs     '{POSITION   vec3}
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

(def gltf-type->num-of-component
  {"SCALAR" 1
   "VEC2"   2
   "VEC3"   3
   "VEC4"   4
   "MAT2"   4
   "MAT3"   9
   "MAT4"   16})

(def grid-vert
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

(def grid-frag
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
  o_color   = vec4(vec3(0.42), g * 0.3);
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

(comment
  gltf-data

  (doto ctx
    (gl blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
    (gl clearColor 0.02 0.02 0.12 1.0)
    (gl clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
    (gl viewport 0 0 width height))

  (let [fov        45.0
        aspect     (/ width height)
        project    (mat/perspective fov aspect 0.1 100)

        up         (v/vec3 0.0 1.0 0.0)
        view-pos   (v/vec3 0.0 18.0 24.0)
        front      (v/vec3 0.0 0.0 -1.0)
        view       (mat/look-at view-pos (m/+ view-pos front) up)

        trans-mat  (m-ext/translation-mat 0.0 0.0 0.0)
        rot-mat    (g/as-matrix (q/quat))
        scale-mat  (m-ext/scaling-mat 0.1)
        model      (reduce m/* [trans-mat rot-mat scale-mat])

        inv-proj   (m/invert project)
        inv-view   (m/invert view)

        p-info     (cljgl/create-program-info ctx grid-vert grid-frag)
        program    (:program p-info)
        vao        (gl ctx createVertexArray)]

    (let [buffer   (gl ctx createBuffer)
          buf-type GL_ARRAY_BUFFER]
      (gl ctx bindBuffer buf-type buffer)
      (gl ctx bufferData buf-type quad GL_STATIC_DRAW)

      (gl ctx bindVertexArray vao)
      (gl ctx bindBuffer buf-type buffer))

    ;; manually see inside gltf, mesh -> primitives -> accessors -> bufferViews
    (let [attr-loc      (-> (:attr-locs p-info) (get 'a_pos) :loc)
          count         (gltf-type->num-of-component "VEC2")
          componentType GL_FLOAT
          byteOffset    0]
      (gl ctx vertexAttribPointer attr-loc count componentType false 0 byteOffset)
      (gl ctx enableVertexAttribArray attr-loc))

    (let [buffer   (gl ctx createBuffer)
          buf-type GL_ELEMENT_ARRAY_BUFFER]
      (gl ctx bindBuffer buf-type buffer)
      (gl ctx bufferData buf-type quad-indices GL_STATIC_DRAW))



    ;; pretend this is render loop
    
    (gl ctx useProgram program)

    (limited-game-loop
     (fn [{:keys [total]}] 
       
       (doto ctx
         (gl blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
         (gl clearColor 0.02 0.02 0.12 1.0)
         (gl clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
         (gl viewport 0 0 width height))
       
       (cljgl/set-uniform ctx p-info 'u_inv_view (f32-arr (vec inv-view)))
       (cljgl/set-uniform ctx p-info 'u_inv_proj (f32-arr (vec inv-proj)))
       (cljgl/set-uniform ctx p-info 'u_cam_pos (f32-arr (into [] view-pos))) 

       (gl ctx disable GL_DEPTH_TEST)
       (gl ctx drawElements GL_TRIANGLES 6 GL_UNSIGNED_INT 0))
     5000))

  :-)
