(ns minusone.rules.model.assimp-js
  (:require
   ["assimpjs" :as assimpjs]
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [minusone.rules.gl.magic :as gl.magic :refer [gl-incantation]]
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.texture :as texture]
   [play-cljc.macros-js :refer-macros [gl]]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(defonce canvas (js/document.querySelector "canvas"))
(defonce gl-context (.getContext canvas "webgl2" (clj->js {:premultipliedAlpha false})))
(defonce width (-> canvas .-clientWidth))
(defonce height (-> canvas .-clientHeight))
(defonce game {:context gl-context})

(defn data-uri->header+Uint8Array [data-uri]
  (let [[header base64-str] (.split data-uri ",")]
    [header (js/Uint8Array.fromBase64 base64-str)]))

;; following this on loading image to bindTexture with blob
;; https://webglfundamentals.org/webgl/lessons/webgl-qna-how-to-load-images-in-the-background-with-no-jank.html
(defn data-uri->ImageBitmap [data-uri callback]
  (let [[header uint8-arr] (data-uri->header+Uint8Array data-uri)
        data-type          (second (re-matches #".*:(.*);.*" header))
        blob               (js/Blob. #js [uint8-arr] #js {:type data-type})]
    (.then (js/createImageBitmap blob)
           (fn [bitmap]
             (let [width  (.-width bitmap)
                   height (.-height bitmap)]
               (callback bitmap width height)
               (println "blob:" data-type "count" (.-length uint8-arr)))))))

(def gltf-type->size
  {"SCALAR" 1
   "VEC2"   2
   "VEC3"   3
   "VEC4"   4
   "MAT2"   4
   "MAT3"   9
   "MAT4"   16})

(defn gltf-magic [gltf-json result-bin]
  (let [mesh        (some-> gltf-json :meshes first)
        accessors   (some-> gltf-json :accessors)
        bufferViews (some-> gltf-json :bufferViews)
        attributes  (some-> mesh :primitives first :attributes)
        indices     (some-> mesh :primitives first :indices)]
    (flatten
     [{:bind-buffer (:name mesh) :buffer-data result-bin :buffer-type (gl game ARRAY_BUFFER)}
      {:bind-vao (:name mesh)}

      (eduction
       (map (fn [[attr-name accessor]]
              (merge {:attr-name attr-name}
                     (get accessors accessor))))
       (map (fn [{:keys [attr-name bufferView byteOffset componentType type]}]
              (let [bufferView (get bufferViews bufferView)]
                {:point-attr (symbol attr-name)
                 :from-shader :DEFAULT-GLTF-SHADER
                 :attr-size (gltf-type->size type)
                 :attr-type componentType
                 :offset (+ (:byteOffset bufferView) byteOffset)})))
       attributes)

      (let [id-accessor   (get accessors indices)
            id-bufferView (get bufferViews (:bufferView id-accessor))
            id-byteOffset (:byteOffset id-bufferView)
            id-byteLength (:byteLength id-bufferView)]
        {:bind-buffer (str (:name mesh) "IBO")
         :buffer-data (.subarray result-bin id-byteOffset (+ id-byteLength id-byteOffset))
         :buffer-type (gl game ELEMENT_ARRAY_BUFFER)})

      {:unbind-vao true}])))

(comment
  (do (gl game clearColor 0.02 0.02 0.0 1.0)
      (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT))))

  (let [files (eduction
               (map #(str "assets/models/" %))
               ["moon.glb"])]
    (-> (assimpjs)
        (.then
         (fn [ajs]
           (-> (->> files
                    (map (fn [file] (-> file js/fetch (.then (fn [res] (.arrayBuffer res))))))
                    js/Promise.all)
               (.then
                (fn [arrayBuffers]
                  (let [ajs-file-list (new (.-FileList ajs))]
                    (dotimes [i (count files)]
                      (.AddFile ajs-file-list (nth files i) (js/Uint8Array. (aget arrayBuffers i))))
                    (let [result (.ConvertFileList ajs ajs-file-list "gltf2")]
                      ;; huh, there is no lint warning
                      (def gltf-json
                        (->> (.GetFile result 0)
                             (.GetContent)
                             (.decode (js/TextDecoder.))
                             (js/JSON.parse)
                             ((fn [json] (-> json (js->clj :keywordize-keys true))))))
                      (def result-bin
                        (->> (.GetFile result 1)
                             (.GetContent))))))))))))

  (#_"all data (w/o images bc it's too big to print in repl)"
   -> gltf-json
   (update :images (fn [images] (map #(update % :uri (juxt type count)) images))))

  (#_"the texture byte array"
   -> gltf-json :images first :uri data-uri->header+Uint8Array
   ((juxt (comp (fn [data-header] (re-matches #"(.*):(.*);(.*)" data-header)) first)
          (comp type second)
          (comp (fn [arr] (.-length arr)) second))))

  (data-uri->ImageBitmap
   (-> gltf-json :images first :uri)
   (fn [bitmap-data width height]
     #_{:clj-kondo/ignore [:inline-def]}
     (def the-texture (texture/texture-incantation game bitmap-data width height 1))))

  (def default-gltf-shader
    (let [vert   {:precision  "mediump float"
                  :inputs     '{POSITION   vec3
                                NORMAL     vec3
                                TEXCOORD_0 vec2}
                  :outputs    '{normal vec3
                                uv vec2}
                  :uniforms   '{u_mvp mat4}
                  :signatures '{main ([] void)}
                  :functions  '{main ([]
                                      (= gl_Position (* u_mvp (vec4 POSITION "1.0")))
                                      (= normal NORMAL)
                                      (= uv TEXCOORD_0))}}
          frag   {:precision  "mediump float"
                  :inputs     '{normal vec3
                                uv vec2}
                  :outputs    '{o_color vec4}
                  :uniforms   '{u_mat sampler2D}
                  :signatures '{main ([] void)}
                  :functions  '{main ([] (= o_color (texture u_mat uv)))}}]
      {:esse-id :DEFAULT-GLTF-SHADER
       :program-data (shader/create-program game vert frag)}))

  (-> default-gltf-shader :program-data)

  (def summons
    (gl-incantation game
                    [default-gltf-shader]
                    (gltf-magic gltf-json result-bin)))

  (let [indices   (let [mesh      (some-> gltf-json :meshes first)
                        accessors (some-> gltf-json :accessors)
                        indices   (some-> mesh :primitives first :indices)]
                    (get accessors indices))
        program   (-> default-gltf-shader :program-data :program)
        uni-loc   (-> default-gltf-shader :program-data :uni-locs)
        u_mvp     (get uni-loc 'u_mvp)
        u_mat     (get uni-loc 'u_mat)
        vao       (nth (first summons) 2)

        trans-mat (m-ext/translation-mat 0.0 0.0 0.0)
        rot-mat   (g/as-matrix (q/quat-from-axis-angle
                                (v/vec3 0.5 0.0 0.0)
                                (m/radians 25.0)))
        scale-mat (m-ext/scaling-mat 1.0)

        model     (reduce m/* [trans-mat rot-mat scale-mat])

        position  (v/vec3 0.0 0.0 3.0)
        front     (v/vec3 0.0 0.0 -1.0)
        up        (v/vec3 0.0 1.0 0.0)
        view      (mat/look-at position (m/+ position front) up)

        fov       45.0
        aspect    (/ width height)
        project   (mat/perspective fov aspect 0.1 100)

        mvp       (reduce m/* [project view model])
        mvp       (f32-arr (vec mvp))]

    (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT)))
    (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
    (gl game viewport 0 0 width height)

    (gl game useProgram program)
    (gl game bindVertexArray vao)
    (gl game uniformMatrix4fv u_mvp false mvp)

    (let [{:keys [tex-unit texture]} the-texture]
      (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
      (gl game bindTexture (gl game TEXTURE_2D) texture)
      (gl game uniform1i u_mat tex-unit))

    (gl game drawElements
        (gl game TRIANGLES)
        (:count indices)
        (:componentType indices)
        0))

  :-)
