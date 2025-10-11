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

(def cube-color-data
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [0.583  0.771  0.014
    0.609  0.115  0.436
    0.327  0.483  0.844
    0.822  0.569  0.201
    0.435  0.602  0.223
    0.310  0.747  0.185
    0.597  0.770  0.761
    0.559  0.436  0.730
    0.359  0.583  0.152
    0.483  0.596  0.789
    0.559  0.861  0.639
    0.195  0.548  0.859
    0.014  0.184  0.576
    0.771  0.328  0.970
    0.406  0.615  0.116
    0.676  0.977  0.133
    0.971  0.572  0.833
    0.140  0.616  0.489
    0.997  0.513  0.064
    0.945  0.719  0.592
    0.543  0.021  0.978
    0.279  0.317  0.505
    0.167  0.620  0.077
    0.347  0.857  0.137
    0.055  0.953  0.042
    0.714  0.505  0.345
    0.783  0.290  0.734
    0.722  0.645  0.174
    0.302  0.455  0.848
    0.225  0.587  0.040
    0.517  0.713  0.338
    0.053  0.959  0.120
    0.393  0.621  0.362
    0.673  0.211  0.457
    0.820  0.883  0.371
    0.982  0.099  0.879]))

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

(def cube-vertex-shader
  {:precision  "mediump float"
   :inputs     '{a_vertex_pos vec3
                 a_color      vec3}
   :outputs    '{v_color vec3}
   :uniforms   '{mvp mat4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position (* mvp (vec4 a_vertex_pos "1.0")))
           (= v_color a_color))}})

(def cube-fragment-shader
  {:precision  "mediump float"
   :inputs     '{v_color vec3}
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([] (= o_color (vec4 v_color "0.5")))}})

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

  (let [vertex-source    (iglu/iglu->glsl (merge {:version glsl-version} cube-vertex-shader))
        fragment-source  (iglu/iglu->glsl (merge {:version glsl-version} cube-fragment-shader))
        cube-program     (gl-utils/create-program game vertex-source fragment-source)

        cube-buffer      (gl-utils/create-buffer game)
        _                (gl game bindBuffer (gl game ARRAY_BUFFER) cube-buffer)
        _                (gl game bufferData (gl game ARRAY_BUFFER) cube-data (gl game STATIC_DRAW))
        vertex-attr      (-> cube-vertex-shader :inputs keys first str)
        vertex-attr-loc  (gl game getAttribLocation cube-program vertex-attr)

        color-buffer     (gl-utils/create-buffer game)
        _                (gl game bindBuffer (gl game ARRAY_BUFFER) color-buffer)
        _                (gl game bufferData (gl game ARRAY_BUFFER) cube-color-data (gl game STATIC_DRAW))
        color-attr       (-> cube-vertex-shader :inputs keys second str)
        color-attr-loc   (gl game getAttribLocation cube-program color-attr)

        uniform-name     (-> cube-vertex-shader :uniforms keys first str)
        uniform-loc      (gl game getUniformLocation cube-program uniform-name)]
    (vswap! (::naive game) assoc
            :cube-program     cube-program
            :cube-vbo         cube-buffer
            :cube-attr-loc    vertex-attr-loc
            :color-buffer     color-buffer
            :color-attr-loc   color-attr-loc
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
            {:keys [vao]} @naive

            aspect-ratio (/ game-width game-height)
            projection   (m/perspective-matrix-3d (m/deg->rad 45) aspect-ratio 0.1 100)
            camera       (m/look-at-matrix-3d [(Math/sin (* total-time 0.005)) 4 3] [0 0 0] [0 1 0])
            view         (m/inverse-matrix-3d camera)
            p*v          (m/multiply-matrices-3d view projection)
            mvp          (#?(:clj float-array :cljs #(js/Float32Array. %)) p*v)]
        (gl game viewport 0 0 game-width game-height)
        (gl game bindVertexArray vao)

        ;; triangle
        (let [{:keys [program vbo attr-loc uniform-loc]} @naive]

          (gl game useProgram program)
          (gl game enableVertexAttribArray attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) vbo)
          (gl game vertexAttribPointer attr-loc 3 (gl game FLOAT) false 0 0)
          (gl game uniformMatrix4fv uniform-loc false mvp)
          (gl game drawArrays (gl game TRIANGLES) 0 3)
          (gl game disableVertexAttribArray attr-loc))

        ;; cube
        (let [{:keys [cube-program
                      cube-vbo cube-attr-loc
                      color-buffer color-attr-loc
                      cube-uniform-loc]} @naive]
          (gl game useProgram cube-program)

          (gl game enableVertexAttribArray cube-attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) cube-vbo)
          (gl game vertexAttribPointer cube-attr-loc 3 (gl game FLOAT) false 0 0)
          
          (gl game enableVertexAttribArray color-attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) color-buffer)
          (gl game vertexAttribPointer color-attr-loc 3 (gl game FLOAT) false 0 0)

          (gl game uniformMatrix4fv cube-uniform-loc false mvp)
          (gl game drawArrays (gl game TRIANGLES) 0 (* 3 12))
          (gl game disableVertexAttribArray cube-attr-loc)))

      #?(:clj  (catch Exception err (throw err))
         :cljs (catch js/Error err (utils/log-limited err "[tick-error]")))))
  (def hmm game)
  game)


(comment 
  (let [game hmm
        vertex-source    (iglu/iglu->glsl (merge {:version glsl-version} cube-vertex-shader))
        fragment-source  (iglu/iglu->glsl (merge {:version glsl-version} cube-fragment-shader))
        cube-program     (gl-utils/create-program game vertex-source fragment-source)]
    (gl game getAttribLocation cube-program "a_color")))
