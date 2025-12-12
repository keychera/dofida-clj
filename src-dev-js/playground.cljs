(ns playground
  (:require
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [minusone.rules.gl.gltf :as gltf]
   [minusone.rules.model.assimp-js :as assimp-js]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_COLOR_BUFFER_BIT
                                  GL_DEPTH_BUFFER_BIT GL_ELEMENT_ARRAY_BUFFER
                                  GL_FLOAT GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA
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

(comment
  gltf-data

  (doto ctx
    (gl blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
    (gl clearColor 0.02 0.02 0.12 1.0)
    (gl clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
    (gl viewport 0 0 width height))

  (let [fov       45.0
        aspect    (/ width height)
        project   (mat/perspective fov aspect 0.1 1000)

        up        (v/vec3 0.0 1.0 0.0)
        view-pos  (v/vec3 0.0 18.0 24.0)
        front     (v/vec3 0.0 0.0 -1.0)
        view      (mat/look-at view-pos (m/+ view-pos front) up)

        trans-mat (m-ext/translation-mat 0.0 0.0 0.0)
        rot-mat   (g/as-matrix (q/quat))
        scale-mat (m-ext/scaling-mat 0.1)
        model     (reduce m/* [trans-mat rot-mat scale-mat])

        p-info    (cljgl/create-program-info ctx vert frag)
        program   (:program p-info)
        vao       (gl ctx createVertexArray)]

    (let [buffer   (gl ctx createBuffer)
          buf-type GL_ARRAY_BUFFER]
      (gl ctx bindBuffer buf-type buffer)
      (gl ctx bufferData buf-type result-bin GL_STATIC_DRAW)

      (gl ctx bindVertexArray vao)
      (gl ctx bindBuffer buf-type buffer))

    ;; manually see inside gltf, mesh -> primitives -> accessors -> bufferViews
    (let [attr-loc      (get (:attr-locs p-info) 'POSITION)
          count         (gltf-type->num-of-component "VEC3")
          componentType GL_FLOAT
          byteOffset    0]
      (gl ctx vertexAttribPointer attr-loc count componentType false 0 byteOffset)
      (gl ctx enableVertexAttribArray attr-loc))

    (let [buffer   (gl ctx createBuffer)
          buf-type GL_ELEMENT_ARRAY_BUFFER]
      (gl ctx bindBuffer buf-type buffer)
      (gl ctx bufferData buf-type (.subarray result-bin 36 48) GL_STATIC_DRAW))

    (gl ctx bindVertexArray nil)

    ;; pretend this is render loop
    
    (gl ctx useProgram program)
    (gl ctx bindVertexArray vao)
    (cljgl/set-uniform ctx p-info 'u_projection (f32-arr (vec project)))
    (cljgl/set-uniform ctx p-info 'u_view (f32-arr (vec view)))
    (cljgl/set-uniform ctx p-info 'u_model (f32-arr (vec model)))

    (gl ctx drawElements GL_TRIANGLES 3 GL_UNSIGNED_INT 0))

  :-)
