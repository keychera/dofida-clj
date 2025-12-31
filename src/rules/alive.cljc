(ns rules.alive
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s-> vars->map]]
   [engine.utils :as utils]
   [engine.world :as world]
   [iglu.core :as iglu]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_COLOR_BUFFER_BIT GL_FLOAT
                                  GL_FRAMEBUFFER GL_ONE GL_ONE_MINUS_SRC_ALPHA
                                  GL_SRC_ALPHA GL_STATIC_DRAW GL_TEXTURE0
                                  GL_TEXTURE_2D GL_TRIANGLES GL_ZERO]]
   [minustwo.gl.texture :as texture]
   [minustwo.systems.input :as input]
   [odoyle.rules :as o]
   [rules.asset :as asset :refer [asset]]
   [rules.primitives :refer [plane3d-uvs plane3d-vertices]]
   [rules.window :as window]))

(def glsl-version #?(:clj "330" :cljs "300 es"))

(def alive-vertex-shader
  {:precision  "mediump float"
   :inputs     '{a_pos  vec3
                 a_uv   vec2}
   :outputs    '{uv     vec2}
   :uniforms   '{u_mvp  mat4
                 u_crop mat3}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position (* u_mvp (vec4 a_pos "1.0")))
           (= uv (.xy (* u_crop (vec3 a_uv "1.0")))))}})

(def alive-fragment-shader
  {:precision  "mediump float"
   :inputs     '{uv      vec2}
   :outputs    '{o_color vec4}
   :uniforms   '{u_tex   sampler2D}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= o_color (texture u_tex uv)))}})

(def view-matrix
  (let [horiz-angle  Math/PI
        verti-angle  0.0
        position     [8.0 -5.0 15.0]

        direction    [(* (Math/cos verti-angle) (Math/sin horiz-angle))
                      (Math/sin verti-angle)
                      (* (Math/cos verti-angle) (Math/cos horiz-angle))]
        right        [(Math/sin (- horiz-angle (/ Math/PI 2)))
                      0.0
                      (Math/cos (- horiz-angle (/ Math/PI 2)))]
        up           (#'#_PLACEHOLDER-cross vector right direction)

        look-at      (#_PLACEHOLDER-look-at-matrix-3d vector position (mapv + position direction) up)
        view         (#_PLACEHOLDER-inverse-matrix-3d vector look-at)

        ;; rotation is part of m
        rotation     (#_PLACEHOLDER-z-rotation-matrix-3d vector (#_PLACEHOLDER-deg->rad vector 180))]
    (#_PLACEHOLDER-multiply-matrices-3d vector rotation view)))

(s/def ::metadata-loaded? boolean?)
(defonce db* (atom {}))

(defn matrices-from-atlas [atlas-metadata frame-name]
  (let [{width  :w
         height :h} (->> atlas-metadata :meta :size)
        frame-crop  (->> atlas-metadata :frames
                         (filter #(= (:filename %) frame-name))
                         (first) :frame)
        {:keys [x y w h]} frame-crop
        scale3d-matrix (#_PLACEHOLDER-scaling-matrix-3d vector (/ w width) (/ h height) 1.0)
        crop-matrix    (#_PLACEHOLDER-multiply-matrices vector
                        (#_PLACEHOLDER-scaling-matrix vector (/ w width) (/ h height))
                        (#_PLACEHOLDER-translation-matrix vector (/ x width) (/ y height)))]
    [scale3d-matrix crop-matrix]))

(def system
  {::world/init-fn
   (fn [world game]
     (let [vertex-source   (iglu/iglu->glsl (merge {:version glsl-version} alive-vertex-shader))
           fragment-source (iglu/iglu->glsl (merge {:version glsl-version} alive-fragment-shader))
           the-program     (cljgl/create-program game vertex-source fragment-source)

           the-attr-loc    (gl game getAttribLocation the-program "a_pos")
           the-uv-attr-loc (gl game getAttribLocation the-program "a_uv")
           the-mvp-loc     (gl game getUniformLocation the-program "u_mvp")
           the-crop-loc    (gl game getUniformLocation the-program "u_crop")
           the-texture-loc (gl game getUniformLocation the-program "u_tex")]
       (swap! db* assoc ::alive-program
              (vars->map the-program the-attr-loc the-uv-attr-loc the-mvp-loc the-crop-loc the-texture-loc)))

     (let [alive-plane-vbo (cljgl/create-buffer game)
           _               (gl game bindBuffer GL_ARRAY_BUFFER alive-plane-vbo)
           _               (gl game bufferData GL_ARRAY_BUFFER plane3d-vertices GL_STATIC_DRAW)

           alive-uv-buffer (cljgl/create-buffer game)
           _               (gl game bindBuffer GL_ARRAY_BUFFER alive-uv-buffer)
           _               (gl game bufferData GL_ARRAY_BUFFER plane3d-uvs GL_STATIC_DRAW)
           vertex-count    6]
       (swap! db* assoc ::alive-plane
              (vars->map alive-plane-vbo alive-uv-buffer vertex-count)))

     (-> world
         (asset ::eye-texture
                #::asset{:type ::asset/texture-from-png :asset-to-load "atlas/eye.png"}
                #::texture{:tex-unit 3})
         (asset ::eye-fbo #::asset{:type ::asset/fbo} #::texture{:tex-unit 4})
         (asset ::eye-atlas
                #::asset{:type ::asset/alive :metadata-to-load "atlas/eye.json"})))

   ::world/rules
   (o/ruleset
    {::birth
     [:what
      [asset-id ::metadata-loaded? true]
      :then
      (println asset-id "is now alive")
      (s-> session
           (o/retract asset-id ::metadata-loaded?)
           (o/insert asset-id ::asset/loaded? true))]

     ::eye
     [:what
      [:rules.dofida/herself :rules.dofida/esse-3d esse-3d] ;; borrow from dofida for now
      [::window/window ::window/dimension window-dim]
      [::eye-texture ::texture/data eye-texture]
      [::eye-fbo ::texture/fbo eye-fbo]
      [::eye-atlas ::asset/loaded? true]]})

   ::world/render-fn
   (fn [world game]
     (when-let [{:keys [window-dim
                        esse-3d
                        eye-texture
                        eye-fbo]} (first (o/query-all world ::eye))]
       (let [{::keys [alive-program alive-plane]} @db*
             {:keys [the-program
                     the-attr-loc
                     the-uv-attr-loc
                     the-mvp-loc
                     the-crop-loc
                     the-texture-loc]} alive-program
             {:keys [alive-plane-vbo
                     alive-uv-buffer
                     vertex-count]} alive-plane
             {:keys [tex-unit texture]} eye-texture]
         (gl game useProgram the-program)

         (gl game enableVertexAttribArray the-attr-loc)
         (gl game bindBuffer GL_ARRAY_BUFFER alive-plane-vbo)
         (gl game vertexAttribPointer the-attr-loc 3 GL_FLOAT false 0 0)

         (gl game enableVertexAttribArray the-uv-attr-loc)
         (gl game bindBuffer GL_ARRAY_BUFFER alive-uv-buffer)
         (gl game vertexAttribPointer the-uv-attr-loc 2 GL_FLOAT false 0 0)

         #_"render to our fbo"
         (gl game bindFramebuffer GL_FRAMEBUFFER (:frame-buf eye-fbo))
         (gl game clearColor 0.0 0.0 0.0 0.0)
         (gl game clear GL_COLOR_BUFFER_BIT)

         (let [atlas-metadata (get-in @db* [::eye-atlas ::metadata])

               width  (:width window-dim)
               height (:height window-dim)
               {:keys [mouse-x mouse-y]} (first (o/query-all world ::input/mouse))
               ratio   (* 0.8 width)
               pupil-x (/ (- (or mouse-x 0.0) (/ width 10)) (- ratio))
               pupil-y (/ (- (or mouse-y 0.0) (/ height 10)) ratio)

               initial-fov    (#_PLACEHOLDER-deg->rad vector 45)
               aspect-ratio   (/ width height)
               projection     (#_PLACEHOLDER-perspective-matrix-3d vector initial-fov aspect-ratio 0.1 100)
               v*p            (#_PLACEHOLDER-multiply-matrices-3d vector view-matrix projection)

               [sclera-scale sclera-crop] (matrices-from-atlas atlas-metadata "sclera.png")
               sclera-mvp     (reduce #_PLACEHOLDER-multiply-matrices-3d vector
                                      [(#_PLACEHOLDER-translation-matrix-3d vector 1.8 0.0 0.0)
                                       (#_PLACEHOLDER-translation-matrix-3d vector (/ pupil-x 5) (/ pupil-y 5) 0.0)
                                       sclera-scale
                                       v*p])
               sclera-mvp-r   (reduce #_PLACEHOLDER-multiply-matrices-3d vector
                                      [(#_PLACEHOLDER-translation-matrix-3d vector 1.8 0.0 0.0)
                                       (#_PLACEHOLDER-translation-matrix-3d vector (/ pupil-x -5) (/ pupil-y 5) 0.0)
                                       (#_PLACEHOLDER-y-rotation-matrix-3d vector (#_PLACEHOLDER-deg->rad vector 180))
                                       sclera-scale
                                       v*p])

               [pupil-scale pupil-crop] (matrices-from-atlas atlas-metadata "pupil.png")
               pupil-mvp     (reduce #_PLACEHOLDER-multiply-matrices-3d vector
                                     [(#_PLACEHOLDER-translation-matrix-3d vector 3.11 0.0 0.0)
                                      (#_PLACEHOLDER-translation-matrix-3d vector pupil-x pupil-y 0.0)
                                      pupil-scale
                                      v*p])
               pupil-mvp-r   (reduce #_PLACEHOLDER-multiply-matrices-3d vector
                                     [(#_PLACEHOLDER-translation-matrix-3d vector 3.11 0.0 0.0)
                                      (#_PLACEHOLDER-translation-matrix-3d vector (- pupil-x) pupil-y 0.0)
                                      (#_PLACEHOLDER-y-rotation-matrix-3d vector (#_PLACEHOLDER-deg->rad vector 180))
                                      pupil-scale
                                      v*p])

               [lashes-scale lashes-crop] (matrices-from-atlas atlas-metadata "lashes.png")
               lashes-mvp     (reduce #_PLACEHOLDER-multiply-matrices-3d vector
                                      [(#_PLACEHOLDER-translation-matrix-3d vector 1.4 0.0 0.0)
                                       (#_PLACEHOLDER-translation-matrix-3d vector (+ 0.18 (/ pupil-x 5))
                                                                (+ -0.5 (/ pupil-y 5)) 0.0)
                                       lashes-scale
                                       v*p])
               lashes-mvp-r   (reduce #_PLACEHOLDER-multiply-matrices-3d vector
                                      [(#_PLACEHOLDER-translation-matrix-3d vector 1.4 0.0 0.0)
                                       (#_PLACEHOLDER-translation-matrix-3d vector (+ 0.18 (/ pupil-x -5))
                                                                (+ -0.5 (/ pupil-y 5)) 0.0)
                                       (#_PLACEHOLDER-y-rotation-matrix-3d vector (#_PLACEHOLDER-deg->rad vector 180))
                                       lashes-scale
                                       v*p])]
           ;; dear god, https://stackoverflow.com/a/49665354/8812880
           ;; I think we encountered this solution several times, but we can only utilize this now
           ;; because our current understanding and our gl pipeline setup
           (gl game blendFuncSeparate GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA GL_ONE GL_ONE)

           (gl game activeTexture (+ GL_TEXTURE0 tex-unit))
           (gl game bindTexture GL_TEXTURE_2D texture)
           (gl game uniform1i the-texture-loc tex-unit)

           (gl game uniformMatrix4fv the-mvp-loc false
               #?(:clj (float-array sclera-mvp)
                  :cljs sclera-mvp))
           (gl game uniformMatrix3fv the-crop-loc false
               #?(:clj (float-array sclera-crop)
                  :cljs sclera-crop))
           (gl game drawArrays GL_TRIANGLES 0 vertex-count)
           (gl game uniformMatrix4fv the-mvp-loc false
               #?(:clj (float-array sclera-mvp-r)
                  :cljs sclera-mvp-r))
           (gl game drawArrays GL_TRIANGLES 0 vertex-count)

           (gl game blendFuncSeparate GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA GL_ZERO GL_ONE)

           (gl game uniformMatrix4fv the-mvp-loc false
               #?(:clj (float-array pupil-mvp)
                  :cljs pupil-mvp))
           (gl game uniformMatrix3fv the-crop-loc false
               #?(:clj (float-array pupil-crop)
                  :cljs pupil-crop))
           (gl game drawArrays GL_TRIANGLES 0 vertex-count)
           (gl game uniformMatrix4fv the-mvp-loc false
               #?(:clj (float-array pupil-mvp-r)
                  :cljs pupil-mvp-r))
           (gl game drawArrays GL_TRIANGLES 0 vertex-count)

           (gl game blendFuncSeparate GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA GL_ONE GL_ONE)

           (gl game uniformMatrix4fv the-mvp-loc false
               #?(:clj (float-array lashes-mvp)
                  :cljs lashes-mvp))
           (gl game uniformMatrix3fv the-crop-loc false
               #?(:clj (float-array lashes-crop)
                  :cljs lashes-crop))
           (gl game drawArrays GL_TRIANGLES 0 vertex-count)
           (gl game uniformMatrix4fv the-mvp-loc false
               #?(:clj (float-array lashes-mvp-r)
                  :cljs lashes-mvp-r))
           (gl game drawArrays GL_TRIANGLES 0 vertex-count))

         #_"render to default fbo"
         (gl game bindFramebuffer GL_FRAMEBUFFER #?(:clj 0 :cljs nil))
         (gl game blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)

         (#_"plane to render from our offscreen texture"
          let [{:keys [off-vao]} (:off-plane esse-3d)
               fbo-tex      (:fbo-tex eye-fbo)
               fbo-tex-unit (:tex-unit eye-fbo)]
          (gl game bindVertexArray off-vao)
          (gl game useProgram the-program)

          (gl game uniformMatrix4fv the-mvp-loc false
              #?(:clj (float-array (#_PLACEHOLDER-identity-matrix vector 4))
                 :cljs (#_PLACEHOLDER-identity-matrix vector 4)))

          (gl game uniformMatrix3fv the-crop-loc false
              #?(:clj (float-array (#_PLACEHOLDER-identity-matrix vector 3))
                 :cljs (#_PLACEHOLDER-identity-matrix vector 3)))

          (gl game activeTexture (+ GL_TEXTURE0 fbo-tex-unit))
          (gl game bindTexture GL_TEXTURE_2D fbo-tex)
          (gl game uniform1i the-texture-loc fbo-tex-unit)

          (gl game drawArrays GL_TRIANGLES 0 6)))))})

(defmethod asset/process-asset ::asset/alive
  [world* _game asset-id {::asset/keys [:rules.asset/metadata-to-load]}]
  (swap! world* #(-> % (o/insert asset-id ::metadata-loaded? false)))
  (utils/get-json
   metadata-to-load
   (fn [loaded-metadata]
     (println "metadata loaded" (:meta loaded-metadata))
     (swap! db* update-in [asset-id ::metadata] merge loaded-metadata)
     (swap! world* #(-> % (o/insert asset-id ::metadata-loaded? true))))))