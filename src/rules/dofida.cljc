(ns rules.dofida
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [assets.asset :as asset :refer [asset]]
   [assets.texture :as texture]
   [clojure.spec.alpha :as s]
   [engine.utils :as utils]
   [engine.world :as world]
   [iglu.core :as iglu]
   [odoyle.rules :as o]
   [play-cljc.gl.utils :as gl-utils]
   [rules.firstperson :as firstperson]))

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
   '{main ([] (= o_color (vec4 "0.42" "1.0" "0.69" "0.9")))}})

(def cube-model
  (-> (utils/load-model-on-compile "assets/defaultcube.obj")
      (utils/model->vertex-data)))

(def dofida-plane
  (-> (utils/load-model-on-compile "assets/dofida-plane.obj")
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
           (= o_color (texture textureSampler uv)))}})

(def off-vb-data
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [-1.0 -1.0,  1.0 -1.0, -1.0  1.0,
    -1.0  1.0,  1.0 -1.0,  1.0  1.0]))

(def off-uv-data
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [0.0 0.0, 1.0 0.0, 0.0 1.0,
    0.0 1.0, 1.0 0.0, 1.0 1.0]))

(def passthrough-shader
  {:precision  "mediump float"
   :inputs     '{a_vertex_pos vec2
                 a_uv         vec2}
   :outputs    '{uv      vec2}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position (vec4 a_vertex_pos.x a_vertex_pos.y "0.0" "1.0"))
           (= uv a_uv))}})

(def simple-texture-shader
  {:precision  "mediump float"
   :inputs     '{uv      vec2}
   :outputs    '{o_color vec4}
   :uniforms   '{textureSampler sampler2D}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= o_color (texture textureSampler uv)))}})

;; jvm docs    https://javadoc.lwjgl.org/org/lwjgl/opengl/GL33.html
;; webgl docs  https://developer.mozilla.org/en-US/docs/Web/API/WebGLRenderingContext

(s/def ::esse-3d map?)

(def system
  {::world/init-fn
   (fn [world game]
     (-> {}
         ((fn set-vao [esse-3d]
            (let [vao (gl game #?(:clj genVertexArrays :cljs createVertexArray))]
              (assoc esse-3d :vao vao))))

         ((fn set-fbo [esse-3d]
            ;; why is it not always clear in the tutorials whether something is done only once or done every frame?? 
            (let [[width height] (utils/get-size game)
                  fbo      (gl game #?(:clj genFramebuffers :cljs createFramebuffer))
                  _        (gl game bindFramebuffer (gl game FRAMEBUFFER) fbo)
                  texture  (gl game #?(:clj genTextures :cljs createTexture))
                  tex-unit 2]

              ;; bind, do stuff, unbind, hmm hmm
              (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
              (gl game bindTexture (gl game TEXTURE_2D) texture)
              (gl game texImage2D (gl game TEXTURE_2D)
                  #_:mip-level    0
                  #_:internal-fmt (gl game RGBA)
                  (int width)
                  (int height)
                  #_:border       0
                  #_:src-fmt      (gl game RGBA)
                  #_:src-type     (gl game UNSIGNED_BYTE)
                  #?(:clj 0 :cljs nil))
              (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MAG_FILTER) (gl game NEAREST))
              (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MIN_FILTER) (gl game NEAREST))
              (gl game bindTexture (gl game TEXTURE_2D) #?(:clj 0 :cljs nil))

              (gl game framebufferTexture2D (gl game FRAMEBUFFER) (gl game COLOR_ATTACHMENT0) (gl game TEXTURE_2D) texture 0)

              (when (not= (gl game checkFramebufferStatus (gl game FRAMEBUFFER)) (gl game FRAMEBUFFER_COMPLETE))
                (println "warning: framebuffer creation incomplete"))
              (gl game bindFramebuffer (gl game FRAMEBUFFER) #?(:clj 0 :cljs nil))

              (assoc esse-3d
                     :fbo fbo
                     :fbo-tex texture
                     :fbo-tex-unit tex-unit))))

         ((fn set-texture-plane [esse-3d]
            (let [vertex-source       (iglu/iglu->glsl (merge {:version glsl-version} passthrough-shader))
                  fragment-source     (iglu/iglu->glsl (merge {:version glsl-version} simple-texture-shader))
                  off-program         (gl-utils/create-program game vertex-source fragment-source)

                  off-vbo             (gl-utils/create-buffer game)
                  _                   (gl game bindBuffer (gl game ARRAY_BUFFER) off-vbo)
                  _                   (gl game bufferData (gl game ARRAY_BUFFER) off-vb-data (gl game STATIC_DRAW))
                  vertex-attr         (-> cube-vertex-shader :inputs keys first str)
                  off-vertex-attr-loc (gl game getAttribLocation off-program vertex-attr)

                  off-uv-buffer       (gl-utils/create-buffer game)
                  _                   (gl game bindBuffer (gl game ARRAY_BUFFER) off-uv-buffer)
                  _                   (gl game bufferData (gl game ARRAY_BUFFER) off-uv-data (gl game STATIC_DRAW))
                  uv-attr             (-> cube-vertex-shader :inputs keys second str)
                  off-uv-attr-loc     (gl game getAttribLocation off-program uv-attr)

                  texture-name        (-> simple-texture-shader :uniforms keys first str)
                  off-texture-loc     (gl game getUniformLocation off-program texture-name)]

              (assoc esse-3d
                     :off-plane
                     {:off-program     off-program
                      :off-vbo         off-vbo
                      :off-attr-loc    off-vertex-attr-loc
                      :off-uv-buffer   off-uv-buffer
                      :off-uv-attr-loc off-uv-attr-loc
                      :off-texture-loc off-texture-loc}))))

         ((fn set-triangle [esse-3d]
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
              (assoc esse-3d
                     :program     triangle-program
                     :vbo         triangle-buffer
                     :attr-loc    vertex-attr-loc
                     :uniform-loc uniform-loc))))

         ((fn set-cube [esse-3d]
            (let [vertex-source   (iglu/iglu->glsl (merge {:version glsl-version} cube-vertex-shader))
                  fragment-source (iglu/iglu->glsl (merge {:version glsl-version} cube-fragment-shader))
                  cube-program    (gl-utils/create-program game vertex-source fragment-source)

                  cube-buffer     (gl-utils/create-buffer game)
                  _               (gl game bindBuffer (gl game ARRAY_BUFFER) cube-buffer)
                  cube-data       (:vertices cube-model)
                  _               (gl game bufferData (gl game ARRAY_BUFFER) cube-data (gl game STATIC_DRAW))
                  vertex-attr     (-> cube-vertex-shader :inputs keys first str)
                  vertex-attr-loc (gl game getAttribLocation cube-program vertex-attr)

                  uniform-name    (-> cube-vertex-shader :uniforms keys first str)
                  uniform-loc     (gl game getUniformLocation cube-program uniform-name)
                  uv-buffer       (gl-utils/create-buffer game)
                  _               (gl game bindBuffer (gl game ARRAY_BUFFER) uv-buffer)
                  cube-uvs        (:uvs cube-model)
                  _               (gl game bufferData (gl game ARRAY_BUFFER) cube-uvs (gl game STATIC_DRAW))
                  uv-attr         (-> cube-vertex-shader :inputs keys second str)
                  uv-attr-loc     (gl game getAttribLocation cube-program uv-attr)

                  texture-name    (-> cube-fragment-shader :uniforms keys first str)
                  texture-loc     (gl game getUniformLocation cube-program texture-name)]

              (assoc esse-3d
                     :cube-program      cube-program
                     :cube-vbo          cube-buffer
                     :cube-vertex-count (:vertex-count cube-model)
                     :cube-attr-loc     vertex-attr-loc
                     :cube-uniform-loc  uniform-loc
                     :uv-buffer         uv-buffer
                     :uv-attr-loc       uv-attr-loc
                     :tex-uniform-loc   texture-loc))))

         ((fn set-plane [esse-3d]
            (let [vertex-source   (iglu/iglu->glsl (merge {:version glsl-version} cube-vertex-shader))
                  fragment-source (iglu/iglu->glsl (merge {:version glsl-version} cube-fragment-shader))
                  program         (gl-utils/create-program game vertex-source fragment-source)

                  model           dofida-plane

                  vbo             (gl-utils/create-buffer game)
                  _               (gl game bindBuffer (gl game ARRAY_BUFFER) vbo)
                  vertices-data   (:vertices model)
                  _               (gl game bufferData (gl game ARRAY_BUFFER) vertices-data (gl game STATIC_DRAW))
                  vertex-attr     (-> cube-vertex-shader :inputs keys first str)
                  vertex-attr-loc (gl game getAttribLocation program vertex-attr)

                  uniform-name    (-> cube-vertex-shader :uniforms keys first str)
                  uniform-loc     (gl game getUniformLocation program uniform-name)
                  uv-buffer       (gl-utils/create-buffer game)
                  _               (gl game bindBuffer (gl game ARRAY_BUFFER) uv-buffer)
                  cube-uvs        (:uvs model)
                  _               (gl game bufferData (gl game ARRAY_BUFFER) cube-uvs (gl game STATIC_DRAW))
                  uv-attr         (-> cube-vertex-shader :inputs keys second str)
                  uv-attr-loc     (gl game getAttribLocation program uv-attr)

                  texture-name    (-> cube-fragment-shader :uniforms keys first str)
                  texture-loc     (gl game getUniformLocation program texture-name)]

              (assoc esse-3d
                     :dofida-plane
                     {:just-program      program
                      :just-vbo          vbo
                      :just-vertex-count (:vertex-count cube-model)
                      :just-attr-loc     vertex-attr-loc
                      :just-uniform-loc  uniform-loc

                      :just-uv-buffer       uv-buffer
                      :just-uv-attr-loc     uv-attr-loc
                      :just-tex-uniform-loc texture-loc}))))

         ((fn enter-the-world [esse-3d]
            (-> world
                (asset ::dofida-texture
                       #::asset{:type ::asset/texture :asset-to-load "dofida.png"}
                       #::texture{:texture-unit 1})
                (o/insert ::herself {::esse-3d esse-3d
                                     ::asset/use ::dofida-texture}))))))

   ::world/rules
   (o/ruleset
    {::esse-3d
     [:what
      [esse-id ::esse-3d esse-3d] ;; contains vao, program, vbo etc, will decomplect later
      [esse-id ::asset/use tex-id]
      [tex-id  ::texture/data texture] ;; (vars->map texture texture-unit)
      [tex-id  ::asset/loaded? true]]})

   ::world/render-fn
   (fn render [world game]
     (when-let [dofida  (first (o/query-all world ::esse-3d))]
       (let [esse-3d (:esse-3d dofida)
             texture (:texture dofida)
             mvp     (:mvp (first (o/query-all world ::firstperson/state)))]
         #_{:clj-kondo/ignore [:inline-def]}
         (def hmm {:world world :game game})
         (gl game bindVertexArray (:vao esse-3d))

         #_"render to our fbo"
         (gl game bindFramebuffer (gl game FRAMEBUFFER) (:fbo esse-3d))
         (gl game clearColor 0.0 0.0 0.0 0.0)
         (gl game clear (gl game COLOR_BUFFER_BIT))

         (#_cube
          let [{:keys [cube-program
                       cube-attr-loc cube-vbo
                       cube-uniform-loc
                       cube-vertex-count
                       uv-buffer uv-attr-loc
                       tex-uniform-loc]} esse-3d
               {:keys [texture-unit texture]} texture]
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
          (gl game uniform1i tex-uniform-loc texture-unit)

          (gl game drawArrays (gl game TRIANGLES) 0 cube-vertex-count)
          (gl game disableVertexAttribArray cube-attr-loc))

         (gl game blendFuncSeparate (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA) (gl game ZERO) (gl game ONE))

         (#_triangle
          when-let [{:keys [program vbo attr-loc uniform-loc]} esse-3d]
          (gl game useProgram program)
          (gl game enableVertexAttribArray attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) vbo)
          (gl game vertexAttribPointer attr-loc 3 (gl game FLOAT) false 0 0)
          (gl game uniformMatrix4fv uniform-loc false mvp)
          (gl game drawArrays (gl game TRIANGLES) 0 3)
          (gl game disableVertexAttribArray attr-loc))

         #_"render to default fbo"
         (gl game bindFramebuffer (gl game FRAMEBUFFER) #?(:clj 0 :cljs nil))
         (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))

         (#_"plane to render from our offscreen texture"
          let [off-plane (:off-plane esse-3d)
               {:keys [off-program
                       off-attr-loc off-vbo
                       off-uv-attr-loc off-uv-buffer
                       off-texture-loc]} off-plane
               fbo-tex      (:fbo-tex esse-3d)
               fbo-tex-unit (:fbo-tex-unit esse-3d)]
          (gl game useProgram off-program)

          (gl game enableVertexAttribArray off-attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) off-vbo)
          (gl game vertexAttribPointer off-attr-loc 2 (gl game FLOAT) false 0 0)

          (gl game enableVertexAttribArray off-uv-attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) off-uv-buffer)
          (gl game vertexAttribPointer off-uv-attr-loc 2 (gl game FLOAT) false 0 0)

          (gl game activeTexture (+ (gl game TEXTURE0) fbo-tex-unit))
          (gl game bindTexture (gl game TEXTURE_2D) fbo-tex)
          (gl game uniform1i off-texture-loc fbo-tex-unit)

          (gl game drawArrays (gl game TRIANGLES) 0 6)
          (gl game disableVertexAttribArray off-attr-loc))

         (#_dofida-plane
          let [plane-3d (:dofida-plane esse-3d)
               {:keys [just-program
                       just-attr-loc just-vbo
                       just-uniform-loc
                       just-vertex-count
                       just-uv-buffer just-uv-attr-loc
                       just-tex-uniform-loc]} plane-3d
               {:keys [texture-unit texture]} texture]
          (gl game useProgram just-program)

          (gl game enableVertexAttribArray just-attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) just-vbo)
          (gl game vertexAttribPointer just-attr-loc 3 (gl game FLOAT) false 0 0)

          (gl game enableVertexAttribArray just-uv-attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) just-uv-buffer)
          (gl game vertexAttribPointer just-uv-attr-loc 2 (gl game FLOAT) false 0 0)

          (gl game uniformMatrix4fv just-uniform-loc false mvp)

          (gl game activeTexture (+ (gl game TEXTURE0) texture-unit))
          (gl game bindTexture (gl game TEXTURE_2D) texture)
          (gl game uniform1i just-tex-uniform-loc texture-unit)

          (gl game drawArrays (gl game TRIANGLES) 0 just-vertex-count)
          (gl game disableVertexAttribArray just-attr-loc)))))})

(comment
  ;; "I think this approximates how big the involved memory per frame"
  ;; "maybe this will be even smaller if the keys' length are optimized"
  (let [facts-str (->> (into []
                             (filter (fn [[_ attr]]
                                       (and (not= attr :assets.asset/db*)
                                            (not= attr :rules.dofida/esse-3d))))
                             (o/query-all (:world hmm)))
                       str)]
    (count facts-str))

  (let [game            (:game hmm)
        vertex-source   (iglu/iglu->glsl (merge {:version glsl-version} cube-vertex-shader))
        fragment-source (iglu/iglu->glsl (merge {:version glsl-version} cube-fragment-shader))
        cube-program    (gl-utils/create-program game vertex-source fragment-source)]
    (gl game getAttribLocation cube-program "a_color")
    [(gl game getParameter (gl game MAX_TEXTURE_IMAGE_UNITS))
     (gl game getParameter (gl game MAX_VERTEX_TEXTURE_IMAGE_UNITS))
     (gl game getParameter (gl game MAX_COMBINED_TEXTURE_IMAGE_UNITS))
     (gl game TEXTURE0)
     (gl game getUniformLocation cube-program "textureSampler")]))
