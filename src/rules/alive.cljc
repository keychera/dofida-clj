(ns rules.alive
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [assets.asset :as asset :refer [asset]]
   [assets.texture :as texture]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s-> vars->map]]
   [engine.utils :as utils]
   [engine.world :as world]
   [iglu.core :as iglu]
   [odoyle.rules :as o]
   [play-cljc.gl.utils :as gl-utils]
   [play-cljc.math :as m]
   [rules.window :as window]))

(def glsl-version #?(:clj "330" :cljs "300 es"))

(def plane3d-vertices
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [-1.0 -1.0 0.0,  1.0 -1.0 0.0, -1.0  1.0 0.0,
    -1.0  1.0 0.0,  1.0 -1.0 0.0,  1.0  1.0 0.0]))

(def plane3d-uv-data
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [0.0 0.0, 1.0 0.0, 0.0 1.0,
    0.0 1.0, 1.0 0.0, 1.0 1.0]))

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

(def static-model-view-matrix
  (let [horiz-angle  (* Math/PI 1.2)
        verti-angle  0.0
        position     [2 0 1]
        ;; still dont understand how to make a flat 2d ui plane... 

        direction    [(* (Math/cos verti-angle) (Math/sin horiz-angle))
                      (Math/sin verti-angle)
                      (* (Math/cos verti-angle) (Math/cos horiz-angle))]
        right        [(Math/sin (- horiz-angle (/ Math/PI 2)))
                      0.0
                      (Math/cos (- horiz-angle (/ Math/PI 2)))]
        up           (#'m/cross right direction)

        look-at      (m/look-at-matrix-3d position (mapv + position direction) up)
        view         (m/inverse-matrix-3d look-at)

        ;; rotation is part of m
        rotation     (m/z-rotation-matrix-3d (m/deg->rad 180))]
    (m/multiply-matrices-3d rotation view)))

(s/def ::metadata-loaded? boolean?)
(def db* (atom {}))

(def system
  {::world/init-fn
   (fn [world game]
     (let [vertex-source   (iglu/iglu->glsl (merge {:version glsl-version} alive-vertex-shader))
           fragment-source (iglu/iglu->glsl (merge {:version glsl-version} alive-fragment-shader))
           the-program     (gl-utils/create-program game vertex-source fragment-source)

           the-attr-loc    (gl game getAttribLocation the-program "a_pos")
           the-uv-attr-loc (gl game getAttribLocation the-program "a_uv")
           the-mvp-loc     (gl game getUniformLocation the-program "u_mvp")
           the-crop-loc    (gl game getUniformLocation the-program "u_crop")
           the-texture-loc (gl game getUniformLocation the-program "u_tex")]
       (swap! db* assoc ::alive-program
              (vars->map the-program the-attr-loc the-uv-attr-loc the-mvp-loc the-crop-loc the-texture-loc)))

     (let [alive-plane-vbo (gl-utils/create-buffer game)
           _               (gl game bindBuffer (gl game ARRAY_BUFFER) alive-plane-vbo)
           _               (gl game bufferData (gl game ARRAY_BUFFER) plane3d-vertices (gl game STATIC_DRAW))

           alive-uv-buffer (gl-utils/create-buffer game)
           _               (gl game bindBuffer (gl game ARRAY_BUFFER) alive-uv-buffer)
           _               (gl game bufferData (gl game ARRAY_BUFFER) plane3d-uv-data (gl game STATIC_DRAW))
           vertex-count    6]
       (swap! db* assoc ::alive-plane
              (vars->map alive-plane-vbo alive-uv-buffer vertex-count)))

     (-> world
         (asset ::eye-texture
                #::asset{:type ::asset/texture :asset-to-load "atlas/eye.png"}
                #::texture{:texture-unit 2})
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
      [::window/window ::window/dimension window-dim]
      [::eye-texture ::texture/data eye-texture]
      [::eye-atlas ::asset/loaded? true]]})

   ::world/render-fn
   (fn [world game]
     (when-let [{:keys [window-dim
                        eye-texture]} (first (o/query-all world ::eye))]
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
             {:keys [texture-unit texture]} eye-texture]
         (gl game useProgram the-program)

         (gl game enableVertexAttribArray the-attr-loc)
         (gl game bindBuffer (gl game ARRAY_BUFFER) alive-plane-vbo)
         (gl game vertexAttribPointer the-attr-loc 3 (gl game FLOAT) false 0 0)

         (gl game enableVertexAttribArray the-uv-attr-loc)
         (gl game bindBuffer (gl game ARRAY_BUFFER) alive-uv-buffer)
         (gl game vertexAttribPointer the-uv-attr-loc 2 (gl game FLOAT) false 0 0)

         (let [initial-fov  (m/deg->rad 45)
               aspect-ratio (/ (:width window-dim) (:height window-dim))
               projection   (m/perspective-matrix-3d initial-fov aspect-ratio 0.1 100)
               mvp          (m/multiply-matrices-3d static-model-view-matrix projection)]
           (gl game uniformMatrix4fv the-mvp-loc false
               #?(:clj (float-array mvp)
                  :cljs mvp)))

         (gl game activeTexture (+ (gl game TEXTURE0) texture-unit))
         (gl game bindTexture (gl game TEXTURE_2D) texture)
         (gl game uniform1i the-texture-loc texture-unit)

         (gl game uniformMatrix3fv the-crop-loc false
             #?(:clj (float-array (m/identity-matrix 3))
                :cljs (m/identity-matrix 3)))

         (gl game drawArrays (gl game TRIANGLES) 0 vertex-count)

         #_(render    "sclera.png"))))})

(defmethod asset/process-asset ::asset/alive
  [world* game asset-id {::asset/keys [metadata-to-load]}]
  (swap! world* #(-> % (o/insert asset-id ::metadata-loaded? false)))
  (utils/get-json
   metadata-to-load
   (fn [loaded-metadata]
     (println "metadata loaded" (:meta loaded-metadata))
     (swap! (::asset/db* game) update-in [asset-id ::metadata] merge loaded-metadata)
     (swap! world* #(-> % (o/insert asset-id ::metadata-loaded? true))))))