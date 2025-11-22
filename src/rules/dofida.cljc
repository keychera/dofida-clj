(ns rules.dofida
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [assets.asset :as asset :refer [asset]]
   [assets.primitives :refer [plane3d-uvs plane3d-vertices]]
   [assets.texture :as texture]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [vars->map]]
   [engine.utils :as utils]
   [engine.world :as world]
   [iglu.core :as iglu]
   [odoyle.rules :as o]
   [play-cljc.gl.utils :as gl-utils]
   [play-cljc.math :as plcjc-m]
   [rules.camera.arcball :as arcball]
   [rules.firstperson :as firstperson]
   [rules.interface.input :as input]
   [rules.window :as window]
   [thi.ng.geom.core :as g]))

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

(def the-vertex-shader
  {:precision  "mediump float"
   :inputs     '{a_pos vec3
                 a_uv         vec2}
   :outputs    '{uv      vec2}
   :uniforms   '{u_mvp mat4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position (* u_mvp (vec4 a_pos "1.0")))
           (= uv a_uv))}})

(def the-fragment-shader
  {:precision  "mediump float"
   :inputs     '{uv      vec2}
   :outputs    '{o_color vec4}
   :uniforms   '{u_tex sampler2D}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= o_color (texture u_tex uv)))}})

(def static-model-view-matrix
  (let [horiz-angle  (* Math/PI 1.5)
        verti-angle  0.0
        position     [2 0 0]

        direction    [(* (Math/cos verti-angle) (Math/sin horiz-angle))
                      (Math/sin verti-angle)
                      (* (Math/cos verti-angle) (Math/cos horiz-angle))]
        right        [(Math/sin (- horiz-angle (/ Math/PI 2)))
                      0.0
                      (Math/cos (- horiz-angle (/ Math/PI 2)))]
        up           (#'plcjc-m/cross right direction)

        look-at      (plcjc-m/look-at-matrix-3d position (mapv + position direction) up)
        view         (plcjc-m/inverse-matrix-3d look-at)

        ;; rotation is part of m
        rotation     (plcjc-m/z-rotation-matrix-3d (plcjc-m/deg->rad 180))]
    (plcjc-m/multiply-matrices-3d rotation view)))

;; jvm docs    https://javadoc.lwjgl.org/org/lwjgl/opengl/GL33.html
;; webgl docs  https://developer.mozilla.org/en-US/docs/Web/API/WebGLRenderingContext

(s/def ::esse-3d map?)

(defn calc-adhoc-mvp [dimension rot-mat]
  ;; will refactor, this ugly
  (let [initial-fov  (plcjc-m/deg->rad 45)
        horiz-angle  Math/PI
        verti-angle  0.0
        position     [0 0 5]
        direction    [(* (Math/cos verti-angle) (Math/sin horiz-angle))
                      (Math/sin verti-angle)
                      (* (Math/cos verti-angle) (Math/cos horiz-angle))]

        right        [(Math/sin (- horiz-angle (/ Math/PI 2)))
                      0
                      (Math/cos (- horiz-angle (/ Math/PI 2)))]

        up           (#'plcjc-m/cross right direction)

        aspect-ratio (/ (:width dimension) (:height dimension))
        projection   (plcjc-m/perspective-matrix-3d initial-fov aspect-ratio 0.1 100)

        camera       (plcjc-m/look-at-matrix-3d position (mapv + position direction) up)
        view         (plcjc-m/inverse-matrix-3d camera)
        p*v          (plcjc-m/multiply-matrices-3d view projection)
        mvp          (plcjc-m/multiply-matrices-3d rot-mat p*v)
        mvp          (#?(:clj float-array :cljs #(js/Float32Array. %)) mvp)]
    mvp))

(defn get-mvp [world dimension]
  (if-let [mode (:mode (first (o/query-all world ::input/mode)))]
    (case mode
      ::input/firstperson (:mvp (first (o/query-all world ::firstperson/state)))
      ::input/arcball
      (let [rot-quat (:new-quat (first (o/query-all world ::arcball/rot-quat)))
            rot-mat  (some-> rot-quat g/as-matrix vec)
            mvp      (some->> rot-mat (calc-adhoc-mvp dimension))]
        (or mvp (plcjc-m/identity-matrix 4))))
    (:mvp (first (o/query-all world ::firstperson/state)))))

;; vao
;; https://stackoverflow.com/questions/8923174/opengl-vao-best-practices
;; https://stackoverflow.com/questions/18485381/opengl-how-many-vaos
;; https://stackoverflow.com/questions/14249634/opengl-vaos-and-multiple-buffers?rq=3
;; https://stackoverflow.com/questions/40438590/is-it-necessary-to-bind-all-vbos-and-textures-each-frame?noredirect=1&lq=1
;; i dont knowwwwww, is switching vao cheap or not???
;; for now let's make vao for each object

(def system
  {::world/init-fn
   (fn [world game]
     (let [vertex-source   (iglu/iglu->glsl (merge {:version glsl-version} the-vertex-shader))
           fragment-source (iglu/iglu->glsl (merge {:version glsl-version} the-fragment-shader))
           the-program     (gl-utils/create-program game vertex-source fragment-source)

           the-attr-loc    (gl game getAttribLocation the-program "a_pos")
           the-uv-attr-loc (gl game getAttribLocation the-program "a_uv")
           the-mvp-loc     (gl game getUniformLocation the-program "u_mvp")
           the-texture-loc (gl game getUniformLocation the-program "u_tex")

           vao (gl game #?(:clj genVertexArrays :cljs createVertexArray))]
       (-> {}
           ((fn set-vao [esse-3d]
              (gl game bindVertexArray vao)
              (assoc esse-3d :vao vao)))

           ((fn set-program [esse-3d]
              (assoc esse-3d
                     :the-program
                     (vars->map the-program the-attr-loc the-uv-attr-loc the-mvp-loc the-texture-loc))))

           ((fn set-off-plane [esse-3d]
              (let [off-vao       (gl game #?(:clj genVertexArrays :cljs createVertexArray))
                    _             (gl game bindVertexArray off-vao)
                    off-vbo       (gl-utils/create-buffer game)
                    _             (gl game bindBuffer (gl game ARRAY_BUFFER) off-vbo)
                    _             (gl game bufferData (gl game ARRAY_BUFFER) plane3d-vertices (gl game STATIC_DRAW))

                    off-uv-buffer (gl-utils/create-buffer game)
                    _             (gl game bindBuffer (gl game ARRAY_BUFFER) off-uv-buffer)
                    _             (gl game bufferData (gl game ARRAY_BUFFER) plane3d-uvs (gl game STATIC_DRAW))]
                
                (gl game enableVertexAttribArray the-attr-loc)
                (gl game bindBuffer (gl game ARRAY_BUFFER) off-vbo)
                (gl game vertexAttribPointer the-attr-loc 3 (gl game FLOAT) false 0 0)
                
                (gl game enableVertexAttribArray the-uv-attr-loc)
                (gl game bindBuffer (gl game ARRAY_BUFFER) off-uv-buffer)
                (gl game vertexAttribPointer the-uv-attr-loc 2 (gl game FLOAT) false 0 0)

                (assoc esse-3d :off-plane (vars->map off-vao)))))

           ((fn set-triangle [esse-3d]
              (gl game bindVertexArray vao)
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
              (let [cube-buffer     (gl-utils/create-buffer game)
                    _               (gl game bindBuffer (gl game ARRAY_BUFFER) cube-buffer)
                    cube-data       (:vertices cube-model)
                    _               (gl game bufferData (gl game ARRAY_BUFFER) cube-data (gl game STATIC_DRAW))

                    uv-buffer       (gl-utils/create-buffer game)
                    _               (gl game bindBuffer (gl game ARRAY_BUFFER) uv-buffer)
                    cube-uvs        (:uvs cube-model)
                    _               (gl game bufferData (gl game ARRAY_BUFFER) cube-uvs (gl game STATIC_DRAW))]

                (assoc esse-3d
                       :cube-vbo          cube-buffer
                       :cube-vertex-count (:vertex-count cube-model)
                       :uv-buffer         uv-buffer))))

           ((fn set-plane [esse-3d]
              (let [program         the-program

                    model           dofida-plane

                    vbo             (gl-utils/create-buffer game)
                    _               (gl game bindBuffer (gl game ARRAY_BUFFER) vbo)
                    vertices-data   (:vertices model)
                    _               (gl game bufferData (gl game ARRAY_BUFFER) vertices-data (gl game STATIC_DRAW))
                    vertex-attr     (-> the-vertex-shader :inputs keys first str)
                    vertex-attr-loc (gl game getAttribLocation program vertex-attr)

                    uniform-name    (-> the-vertex-shader :uniforms keys first str)
                    uniform-loc     (gl game getUniformLocation program uniform-name)
                    uv-buffer       (gl-utils/create-buffer game)
                    _               (gl game bindBuffer (gl game ARRAY_BUFFER) uv-buffer)
                    cube-uvs        (:uvs model)
                    _               (gl game bufferData (gl game ARRAY_BUFFER) cube-uvs (gl game STATIC_DRAW))
                    uv-attr         (-> the-vertex-shader :inputs keys second str)
                    uv-attr-loc     (gl game getAttribLocation program uv-attr)

                    texture-name    (-> the-fragment-shader :uniforms keys first str)
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
                         #::asset{:type ::asset/texture-from-png :asset-to-load "dofida.png"}
                         #::texture{:tex-unit 1})
                  (asset ::dofida-fbo #::asset{:type ::asset/fbo} #::texture{:tex-unit 2})
                  (o/insert ::herself {::esse-3d esse-3d
                                       ::asset/use ::dofida-texture})))))))

   ::world/rules
   (o/ruleset
    {::esse-3d
     [:what
      [esse-id ::esse-3d esse-3d] ;; contains vao, program, vbo etc, will decomplect later
      [esse-id ::asset/use tex-id]
      [tex-id  ::texture/from-png texture] ;; (vars->map texture tex-unit)
      [::dofida-fbo ::texture/fbo fbo]     ;; (vars->map frame-buf fbo-tex tex-unit), hardcoded-id for now
      [tex-id ::asset/loaded? true]]})

   ::world/render-fn
   (fn render [world game]
     (when-let [dofida (first (o/query-all world ::esse-3d))]
       (let [esse-3d (:esse-3d dofida)
             texture (:texture dofida)
             fbo     (:fbo dofida)
             dim     (:dimension (first (o/query-all world ::window/window)))
             mvp     (get-mvp world dim)
             {:keys [the-program
                     the-attr-loc
                     the-uv-attr-loc
                     the-mvp-loc
                     the-texture-loc]} (:the-program esse-3d)]
         #_{:clj-kondo/ignore [:inline-def]} ;; debugging purposes
         (def hmm {:world world :game game})

         (gl game bindVertexArray (:vao esse-3d))
         #_"render to our fbo"
         (gl game bindFramebuffer (gl game FRAMEBUFFER) (:frame-buf fbo))
         (gl game clearColor 1.0 1.0 1.0 0.0)
         (gl game clear (gl game COLOR_BUFFER_BIT))

         (#_cube
          let [{:keys [cube-vbo cube-vertex-count uv-buffer]} esse-3d
               {:keys [tex-unit texture]} texture]
          (gl game useProgram the-program)

          (gl game enableVertexAttribArray the-attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) cube-vbo)
          (gl game vertexAttribPointer the-attr-loc 3 (gl game FLOAT) false 0 0)

          (gl game enableVertexAttribArray the-uv-attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) uv-buffer)
          (gl game vertexAttribPointer the-uv-attr-loc 2 (gl game FLOAT) false 0 0)

          (gl game uniformMatrix4fv the-mvp-loc false mvp)

          (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
          (gl game bindTexture (gl game TEXTURE_2D) texture)
          (gl game uniform1i the-texture-loc tex-unit)

          (gl game drawArrays (gl game TRIANGLES) 0 cube-vertex-count)
          (gl game disableVertexAttribArray the-attr-loc))

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
          let [{:keys [off-vao]} (:off-plane esse-3d)
               fbo-tex      (:fbo-tex fbo)
               fbo-tex-unit (:tex-unit fbo)]
          (gl game bindVertexArray off-vao)
          (gl game useProgram the-program)

          (gl game uniformMatrix4fv the-mvp-loc false
              #?(:clj (float-array (plcjc-m/identity-matrix 4))
                 :cljs (plcjc-m/identity-matrix 4)))

          (gl game activeTexture (+ (gl game TEXTURE0) fbo-tex-unit))
          (gl game bindTexture (gl game TEXTURE_2D) fbo-tex)
          (gl game uniform1i the-texture-loc fbo-tex-unit)

          (gl game drawArrays (gl game TRIANGLES) 0 6))

         (#_dofida-plane
          let [{:keys [just-vbo just-vertex-count just-uv-buffer]} (:dofida-plane esse-3d)
               {:keys [tex-unit texture]} texture]
          (gl game bindVertexArray (:vao esse-3d))

          (gl game enableVertexAttribArray the-attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) just-vbo)
          (gl game vertexAttribPointer the-attr-loc 3 (gl game FLOAT) false 0 0)

          (gl game enableVertexAttribArray the-uv-attr-loc)
          (gl game bindBuffer (gl game ARRAY_BUFFER) just-uv-buffer)
          (gl game vertexAttribPointer the-uv-attr-loc 2 (gl game FLOAT) false 0 0)

          (let [initial-fov  (plcjc-m/deg->rad 45)
                aspect-ratio (/ (:width dim) (:height dim))
                projection   (plcjc-m/perspective-matrix-3d initial-fov aspect-ratio 0.1 100)
                mvp          (plcjc-m/multiply-matrices-3d static-model-view-matrix projection)]
            (gl game uniformMatrix4fv the-mvp-loc false
                #?(:clj (float-array mvp)
                   :cljs mvp)))

          (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
          (gl game bindTexture (gl game TEXTURE_2D) texture)
          (gl game uniform1i the-texture-loc tex-unit)

          (gl game drawArrays (gl game TRIANGLES) 0 just-vertex-count)
          (gl game disableVertexAttribArray the-attr-loc)))))})

(comment
  ;; "I think this approximates how big the involved memory per frame"
  ;; "maybe this will be even smaller if the keys' length are optimized"
  (let [facts-str (->> (into []
                             (filter (fn [[_ attr]]
                                       (not= attr :rules.dofida/esse-3d)))
                             (o/query-all (:world hmm)))
                       str)]
    (count facts-str))

  (let [game            (:game hmm)
        vertex-source   (iglu/iglu->glsl (merge {:version glsl-version} the-vertex-shader))
        fragment-source (iglu/iglu->glsl (merge {:version glsl-version} the-fragment-shader))
        cube-program    (gl-utils/create-program game vertex-source fragment-source)]
    (gl game getAttribLocation cube-program "a_color")
    [(gl game getParameter (gl game MAX_TEXTURE_IMAGE_UNITS))
     (gl game getParameter (gl game MAX_VERTEX_TEXTURE_IMAGE_UNITS))
     (gl game getParameter (gl game MAX_COMBINED_TEXTURE_IMAGE_UNITS))
     (gl game TEXTURE0)
     (gl game getUniformLocation cube-program "textureSampler")]))
