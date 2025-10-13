(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [com.rpl.specter :as sp]
   [engine.refresh :refer [*refresh?]]
   [engine.utils :as utils]
   [engine.world :as world]
   [iglu.core :as iglu]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.utils :as gl-utils]
   [rules.firstperson :as firstperson]
   [rules.interface.input :as input]
   [rules.time :as time]
   [rules.window :as window]))

;; now control https://www.opengl-tutorial.org/beginners-tutorials/tutorial-6-keyboard-and-mouse/

(def glsl-version #?(:clj "330" :cljs "300 es"))

(def triangle-data
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [-1.0 -1.0 0.0
    1.0 -1.0 0.0
    0.0  1.0 0.0]))

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

(def cube-model
  (-> (utils/load-model-on-compile "assets/defaultcube.obj")
      (utils/model->vertex-data)))

(def cube-vertex-shader
  {:precision  "mediump float"
   :inputs     '{a_vertex_pos vec3
                 a_uv         vec2}
   :outputs    '{uv      vec2}
   :uniforms   '{mvp mat4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position (* mvp (vec4 a_vertex_pos "1.0")))
           (= uv a_uv))}})

(def cube-fragment-shader
  {:precision  "mediump float"
   :inputs     '{v_color vec3
                 uv      vec2}
   :outputs    '{o_color vec4}
   :uniforms   '{textureSampler sampler2D}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           ("if" (> uv.x "0.001") (= o_color (texture textureSampler uv)))
           ("else" (= o_color (vec4 "1.0" "1.0" "1.0" "0.2"))))}})

(defn ->game [context]
  (merge
   (c/->game context)
   {::global* (atom {})}
   (world/->init)))

(def all-systems
  [window/system
   input/system
   firstperson/system])

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))

  (let [all-rules  (apply concat (sp/select [sp/ALL ::world/rules] all-systems))
        all-init   (sp/select [sp/ALL ::world/init-fn some?] all-systems)
        reload-fns (sp/select [sp/ALL ::world/reload-fn some?] all-systems)
        render-fns (sp/select [sp/ALL ::world/render-fn some?] all-systems)]

    (swap! (::world/init-cnt* game) inc)
    (swap! (::global* game) assoc ::render-fns render-fns)
    (swap! (::world/atom* game)
           (fn [world]
             (-> (world/init-world world game all-rules reload-fns)
                 (as-> w (reduce (fn [w' init-fn] (init-fn w' game)) w all-init))
                 (o/fire-rules)))))

  (let [vao (gl game #?(:clj genVertexArrays :cljs createVertexArray))]
    (swap! (::global* game) assoc :vao vao))

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
    (swap! (::global* game) assoc
           :program     triangle-program
           :vbo         triangle-buffer
           :attr-loc    vertex-attr-loc
           :uniform-loc uniform-loc))

  (let [vertex-source    (iglu/iglu->glsl (merge {:version glsl-version} cube-vertex-shader))
        fragment-source  (iglu/iglu->glsl (merge {:version glsl-version} cube-fragment-shader))
        cube-program     (gl-utils/create-program game vertex-source fragment-source)

        cube-buffer      (gl-utils/create-buffer game)
        _                (gl game bindBuffer (gl game ARRAY_BUFFER) cube-buffer)
        cube-data        (:vertices cube-model)
        _                (gl game bufferData (gl game ARRAY_BUFFER) cube-data (gl game STATIC_DRAW))
        vertex-attr      (-> cube-vertex-shader :inputs keys first str)
        vertex-attr-loc  (gl game getAttribLocation cube-program vertex-attr)

        uniform-name     (-> cube-vertex-shader :uniforms keys first str)
        uniform-loc      (gl game getUniformLocation cube-program uniform-name)]
    (swap! (::global* game) assoc
           :cube-program      cube-program
           :cube-vbo          cube-buffer
           :cube-vertex-count (:vertex-count cube-model)
           :cube-attr-loc     vertex-attr-loc
           :cube-uniform-loc  uniform-loc)

    (utils/get-image
     "dofida.png"
     (fn [{:keys [data width height]}]
       (let [uv-buffer    (gl-utils/create-buffer game)
             _            (gl game bindBuffer (gl game ARRAY_BUFFER) uv-buffer)
             cube-uvs     (:uvs cube-model)
             _            (gl game bufferData (gl game ARRAY_BUFFER) cube-uvs (gl game STATIC_DRAW))
             uv-attr      (-> cube-vertex-shader :inputs keys second str)
             uv-attr-loc  (gl game getAttribLocation cube-program uv-attr)

             texture-unit #_(swap! (:tex-count game) inc) 0 ;; disabling multi texture for reloading-ease
             texture      (gl game #?(:clj genTextures :cljs createTexture))

             texture-name (-> cube-fragment-shader :uniforms keys first str)
             texture-loc  (gl game getUniformLocation cube-program texture-name)]

         (gl game activeTexture (+ (gl game TEXTURE0) texture-unit))
         (gl game bindTexture (gl game TEXTURE_2D) texture)

         (gl game texImage2D (gl game TEXTURE_2D)
             #_:mip-level    0
             #_:internal-fmt (gl game RGBA)
             (int width)
             (int height)
             #_:border       0
             #_:src-fmt      (gl game RGBA)
             #_:src-type     (gl game UNSIGNED_BYTE)
             data)

         (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MAG_FILTER) (gl game NEAREST))
         (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MIN_FILTER) (gl game NEAREST))

         (swap! (::global* game) assoc
                :uv-buffer uv-buffer
                :uv-attr-loc uv-attr-loc
                :texture-unit texture-unit
                :texture texture
                :texture-loc texture-loc))))))

;; jvm docs    https://javadoc.lwjgl.org/org/lwjgl/opengl/GL33.html
;; webgl docs  https://developer.mozilla.org/en-US/docs/Web/API/WebGLRenderingContext

(defn tick [game]
  #_{:clj-kondo/ignore [:inline-def]}
  (def hmm game)
  (if @*refresh?
    (try (println "refresh game")
         (swap! *refresh? not)
         (init game)
         #?(:clj  (catch Exception err (throw err))
            :cljs (catch js/Error err
                    (utils/log-limited err "[init-error]"))))
    (try
      (let [[game-width game-height] (utils/get-size game)
            {:keys [total-time delta-time ::global*]} game
            {:keys [vao]} @global*

            world (swap! (::world/atom* game)
                         #(-> %
                              (window/set-window game-width game-height)
                              (time/insert total-time delta-time)
                              (o/fire-rules)))
            mvp    (:mvp (first (o/query-all world ::firstperson/state)))]

        (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT)))
        (gl game viewport 0 0 game-width game-height)
        (gl game bindVertexArray vao)

        ;; triangle
        (let [{:keys [program vbo attr-loc uniform-loc]} @global*]

          (gl game useProgram program)
          (gl game enableVertexAttribArray attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) vbo)
          (gl game vertexAttribPointer attr-loc 3 (gl game FLOAT) false 0 0)
          (gl game uniformMatrix4fv uniform-loc false mvp)
          (gl game drawArrays (gl game TRIANGLES) 0 3)
          (gl game disableVertexAttribArray attr-loc))

        ;; cube
        (let [{:keys [cube-program
                      cube-attr-loc cube-vbo
                      cube-uniform-loc
                      cube-vertex-count

                      uv-attr-loc uv-buffer
                      texture-loc texture-unit texture]} @global*]
          (when (and uv-attr-loc uv-buffer)
            (gl game useProgram cube-program)

            (gl game enableVertexAttribArray cube-attr-loc)
            (gl game bindBuffer (gl game ARRAY_BUFFER) cube-vbo)
            (gl game vertexAttribPointer cube-attr-loc 3 (gl game FLOAT) false 0 0)

            (gl game enableVertexAttribArray uv-attr-loc)
            (gl game bindBuffer (gl game ARRAY_BUFFER) uv-buffer)
            (gl game vertexAttribPointer uv-attr-loc 2 (gl game FLOAT) false 0 0)

            (gl game uniformMatrix4fv cube-uniform-loc false mvp)

            (gl game activeTexture (+ (gl game TEXTURE0) texture-unit))
            (gl game bindTexture (gl game TEXTURE_2D) texture)
            (gl game uniform1i texture-loc texture-unit)

            (gl game drawArrays (gl game TRIANGLES) 0 cube-vertex-count)
            (gl game disableVertexAttribArray cube-attr-loc))))

      #?(:clj  (catch Exception err (throw err))
         :cljs (catch js/Error err
                 (utils/log-limited err "[tick-error]")))))
  game)

(comment
  (let [game hmm
        vertex-source    (iglu/iglu->glsl (merge {:version glsl-version} cube-vertex-shader))
        fragment-source  (iglu/iglu->glsl (merge {:version glsl-version} cube-fragment-shader))
        cube-program     (gl-utils/create-program game vertex-source fragment-source)]
    (gl game getAttribLocation cube-program "a_color")
    [(gl game getParameter (gl game MAX_TEXTURE_IMAGE_UNITS))
     (gl game getParameter (gl game MAX_VERTEX_TEXTURE_IMAGE_UNITS))
     (gl game getParameter (gl game MAX_COMBINED_TEXTURE_IMAGE_UNITS))
     (gl game TEXTURE0)
     (gl game getUniformLocation cube-program "textureSampler")])

  (let [world @(::world/atom* hmm)]
    (o/query-all world ::input/mouse)))
