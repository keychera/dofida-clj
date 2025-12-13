(ns playground
  (:require
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [engine.world :as world]
   [minusone.rules.gizmo.perspective-grid :as perspective-grid]
   [minusone.rules.gl.gltf :as gltf]
   [minusone.rules.model.assimp-js :as assimp-js]
   [minustwo.engine :as engine]
   [minustwo.game :as game]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_COLOR_BUFFER_BIT
                                  GL_DEPTH_BUFFER_BIT GL_DEPTH_TEST
                                  GL_ELEMENT_ARRAY_BUFFER GL_FLOAT
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA
                                  GL_STATIC_DRAW GL_TRIANGLES GL_UNSIGNED_INT]]
   [minustwo.gl.macros :refer [webgl] :rename {webgl gl}]
   [minustwo.systems :as systems]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]
   [odoyle.rules :as o]))

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

(def adhoc-system
  {::world/init-fn
   (fn [world _game]
     (println "adhoc system running!")
     world)})

(comment
  (with-redefs
   [systems/all (conj systems/all adhoc-system)]
    (-> (game/->game {:webgl-context ctx
                      :total-time 0
                      :delta-time 0})
        (engine/init)
        (engine/tick)
        ((comp o/query-all deref ::world/atom*))))

  (doto ctx
    (gl blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
    (gl clearColor 0.02 0.02 0.12 1.0)
    (gl clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
    (gl viewport 0 0 width height))

  (let [fov        45.0
        aspect     (/ width height)
        project    (mat/perspective fov aspect 0.1 100)

        up         (v/vec3 0.0 1.0 0.0)
        view-pos   (v/vec3 0.0 2.0 24.0)
        front      (v/vec3 0.0 0.0 -1.0)
        view       (mat/look-at view-pos (m/+ view-pos front) up)

        trans-mat  (m-ext/translation-mat 0.0 1.0 0.0)
        rot-mat    (g/as-matrix (q/quat))
        scale-mat  (m-ext/scaling-mat 5.0)
        model      (reduce m/* [trans-mat rot-mat scale-mat])

        inv-proj   (m/invert project)
        inv-view   (m/invert view)

        grid-prog  (cljgl/create-program-info ctx perspective-grid/vert perspective-grid/frag)
        grid-vao   (gl ctx createVertexArray)

        gltf-prog  (cljgl/create-program-info ctx vert frag)
        gltf-vao   (gl ctx createVertexArray)]

    (let [vao           grid-vao
          buffer        perspective-grid/quad
          indices       perspective-grid/quad-indices
          vbo           (gl ctx createBuffer)

          attr-loc      (-> (:attr-locs grid-prog) (get 'a_pos) :loc)
          count         (gltf-type->num-of-component "VEC2")
          componentType GL_FLOAT
          byteOffset    0

          ibo           (gl ctx createBuffer)]
      (doto ctx
        (gl bindBuffer GL_ARRAY_BUFFER vbo)
        (gl bufferData GL_ARRAY_BUFFER buffer GL_STATIC_DRAW)
        (gl bindVertexArray vao)
        (gl bindBuffer GL_ARRAY_BUFFER vbo))

      (doto ctx
        (gl vertexAttribPointer attr-loc count componentType false 0 byteOffset)
        (gl enableVertexAttribArray attr-loc))

      (doto ctx
        (gl bindBuffer GL_ELEMENT_ARRAY_BUFFER ibo)
        (gl bufferData GL_ELEMENT_ARRAY_BUFFER indices GL_STATIC_DRAW))

      (gl ctx bindVertexArray nil))

    ;; manually see inside gltf, mesh -> primitives -> accessors -> bufferViews 
    (let [vao           gltf-vao
          buffer        result-bin
          indices       (.subarray result-bin 36 48)
          vbo           (gl ctx createBuffer)

          attr-loc      (-> (:attr-locs grid-prog) (get 'POSITION) :loc)
          count         (gltf-type->num-of-component "VEC3")
          componentType GL_FLOAT
          byteOffset    0

          ibo           (gl ctx createBuffer)]
      (doto ctx
        (gl bindBuffer GL_ARRAY_BUFFER vbo)
        (gl bufferData GL_ARRAY_BUFFER buffer GL_STATIC_DRAW)
        (gl bindVertexArray vao)
        (gl bindBuffer GL_ARRAY_BUFFER vbo))

      (doto ctx
        (gl vertexAttribPointer attr-loc count componentType false 0 byteOffset)
        (gl enableVertexAttribArray attr-loc))

      (doto ctx
        (gl bindBuffer GL_ELEMENT_ARRAY_BUFFER ibo)
        (gl bufferData GL_ELEMENT_ARRAY_BUFFER indices GL_STATIC_DRAW))

      (gl ctx bindVertexArray nil))

    (limited-game-loop
     (fn [_]
       (doto ctx
         (gl blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
         (gl clearColor 0.02 0.02 0.12 1.0)
         (gl clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
         (gl viewport 0 0 width height))

       (doto ctx
         (gl useProgram (:program grid-prog))
         (gl bindVertexArray grid-vao)

         (cljgl/set-uniform grid-prog 'u_inv_view (f32-arr (vec inv-view)))
         (cljgl/set-uniform grid-prog 'u_inv_proj (f32-arr (vec inv-proj)))
         (cljgl/set-uniform grid-prog 'u_cam_pos (f32-arr (into [] view-pos)))

         (gl disable GL_DEPTH_TEST)
         (gl drawElements GL_TRIANGLES 6 GL_UNSIGNED_INT 0)
         (gl enable GL_DEPTH_TEST))

       (doto ctx
         (gl useProgram (:program gltf-prog))
         (gl bindVertexArray gltf-vao)

         (cljgl/set-uniform gltf-prog 'u_projection (f32-arr (vec project)))
         (cljgl/set-uniform gltf-prog 'u_view (f32-arr (vec view)))
         (cljgl/set-uniform gltf-prog 'u_model (f32-arr (vec model)))

         (gl drawElements GL_TRIANGLES 3 GL_UNSIGNED_INT 0)))
     5000))

  :-)
