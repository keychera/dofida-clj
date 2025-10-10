(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.refresh :refer [*refresh?]]
   [engine.utils :as utils]
   [iglu.core :as iglu]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.utils :as gl-utils]
   [play-cljc.math :as m]))

;; from the very beginning https://www.opengl-tutorial.org/beginners-tutorials/tutorial-3-matrices/

(def glsl-version #?(:clj "330" :cljs "300 es"))

(defn ->game [context]
  (merge
   (c/->game context)
   {::naive (volatile! {})}))

(def triangle-data
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [-1.0 -1.0 0.0
    1.0 -1.0 0.0
    0.0  1.0 0.0]))

(def cube-data
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [-1.0 -1.0 -1.0
    -1.0 -1.0  1.0
    -1.0  1.0  1.0
    1.0  1.0 -1.0
    -1.0 -1.0 -1.0
    -1.0  1.0 -1.0
    1.0 -1.0  1.0
    -1.0 -1.0 -1.0
    1.0 -1.0 -1.0
    1.0  1.0 -1.0
    1.0 -1.0 -1.0
    -1.0 -1.0 -1.0
    -1.0 -1.0 -1.0
    -1.0  1.0  1.0
    -1.0  1.0 -1.0
    1.0 -1.0  1.0
    -1.0 -1.0  1.0
    -1.0 -1.0 -1.0
    -1.0  1.0  1.0
    -1.0 -1.0  1.0
    1.0 -1.0  1.0
    1.0  1.0  1.0
    1.0 -1.0 -1.0
    1.0  1.0 -1.0
    1.0 -1.0 -1.0
    1.0  1.0  1.0
    1.0 -1.0  1.0
    1.0  1.0  1.0
    1.0  1.0 -1.0
    -1.0  1.0 -1.0
    1.0  1.0  1.0
    -1.0  1.0 -1.0
    -1.0  1.0  1.0
    1.0  1.0  1.0
    -1.0  1.0  1.0
    1.0 -1.0  1.0]))

(def vertex-shader
  {:precision  "mediump float"
   :inputs     '{a_vertex_pos vec3}
   :uniforms   '{mvp mat4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position (* mvp (vec4 a_vertex_pos "1.0"))))}})

(def fragment-shader
  {:precision  "mediump float"
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([] (= o_color (vec4 "0.42" "1.0" "0.69" "0.5")))}})

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))

  (let [vao (gl game #?(:clj genVertexArrays :cljs createVertexArray))]
    (vswap! (::naive game) assoc :vao vao))

  (let [vertex-source    (iglu/iglu->glsl (merge {:version glsl-version} vertex-shader))
        fragment-source  (iglu/iglu->glsl (merge {:version glsl-version} fragment-shader))
        triangle-program (gl-utils/create-program game vertex-source fragment-source)
        triangle-buffer  (gl-utils/create-buffer game)
        _                (gl game bindBuffer (gl game ARRAY_BUFFER) triangle-buffer)
        _                (gl game bufferData (gl game ARRAY_BUFFER) triangle-data (gl game STATIC_DRAW))
        attr-name        (-> vertex-shader :inputs keys first str)
        vertex-attr-loc  (gl game getAttribLocation triangle-program attr-name)
        uniform-name     (-> vertex-shader :uniforms keys first str)
        uniform-loc      (gl game getUniformLocation triangle-program uniform-name)]
    (vswap! (::naive game) assoc
            :program     triangle-program
            :vbo         triangle-buffer
            :attr-loc    vertex-attr-loc
            :uniform-loc uniform-loc))

  (let [vertex-source    (iglu/iglu->glsl (merge {:version glsl-version} vertex-shader))
        fragment-source  (iglu/iglu->glsl (merge {:version glsl-version} fragment-shader))
        cube-program     (gl-utils/create-program game vertex-source fragment-source)
        cube-buffer      (gl-utils/create-buffer game)
        _                (gl game bindBuffer (gl game ARRAY_BUFFER) cube-buffer)
        _                (gl game bufferData (gl game ARRAY_BUFFER) cube-data (gl game STATIC_DRAW))
        attr-name        (-> vertex-shader :inputs keys first str)
        vertex-attr-loc  (gl game getAttribLocation cube-program attr-name)
        uniform-name     (-> vertex-shader :uniforms keys first str)
        uniform-loc      (gl game getUniformLocation cube-program uniform-name)]
    (vswap! (::naive game) assoc
            :cube-program     cube-program
            :cube-vbo         cube-buffer
            :cube-attr-loc    vertex-attr-loc
            :cube-uniform-loc uniform-loc)))

;; jvm docs    https://javadoc.lwjgl.org/org/lwjgl/opengl/GL33.html
;; webgl docs  https://developer.mozilla.org/en-US/docs/Web/API/WebGLRenderingContext

(defn tick [game]
  (if @*refresh?
    (try (println "calling (init game)")
         (swap! *refresh? not)
         (init game)
         #?(:clj  (catch Exception err (throw err))
            :cljs (catch js/Error err (utils/log-limited err "[init-error]"))))
    (try
      (let [[game-width game-height] (utils/get-size game)
            {:keys [total-time ::naive]} game
            {:keys [vao
                    program vbo attr-loc uniform-loc
                    cube-program cube-vbo cube-attr-loc cube-uniform-loc]} @naive

            aspect-ratio (/ game-width game-height)
            projection   (m/perspective-matrix-3d (m/deg->rad 45) aspect-ratio 0.1 100)
            camera       (m/look-at-matrix-3d [4 4 3] [0 0 0] [0 1 0])
            view         (m/inverse-matrix-3d camera)
            p*v          (m/multiply-matrices-3d view projection)
            mvp          (#?(:clj float-array :cljs #(js/Float32Array. %)) p*v)]

        (gl game viewport 0 0 game-width game-height)
        (gl game bindVertexArray vao)

        ;; triangle
        (gl game useProgram program)
        (gl game enableVertexAttribArray attr-loc)
        (gl game bindBuffer (gl game ARRAY_BUFFER) vbo)
        (gl game vertexAttribPointer attr-loc 3 (gl game FLOAT) false 0 0)
        (gl game uniformMatrix4fv uniform-loc false mvp)
        (gl game drawArrays (gl game TRIANGLES) 0 3)
        (gl game disableVertexAttribArray attr-loc)

        ;; cube
        (gl game useProgram cube-program)
        (gl game enableVertexAttribArray cube-attr-loc)
        (gl game bindBuffer (gl game ARRAY_BUFFER) cube-vbo)
        (gl game vertexAttribPointer cube-attr-loc 3 (gl game FLOAT) false 0 0)
        (gl game uniformMatrix4fv cube-uniform-loc false mvp)
        (gl game drawArrays (gl game TRIANGLES) 0 (* 3 12))
        (gl game disableVertexAttribArray cube-attr-loc))
      #?(:clj  (catch Exception err (throw err))
         :cljs (catch js/Error err (utils/log-limited err "[tick-error]")))))
  game)
