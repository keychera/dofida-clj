(ns minusone.rules.model.assimp-js
  (:require
   ["assimpjs" :as assimpjs]
   [play-cljc.macros-js :refer-macros [gl]]
   [minusone.rules.gl.magic :as gl.magic :refer [gl-incantation]]
   [minusone.rules.gl.shader :as shader]
   [engine.sugar :refer [f32-arr]]
   [thi.ng.geom.matrix :as mat]))

(defonce gl-context
  (-> (js/document.querySelector "canvas")
      (.getContext "webgl2" (clj->js {:premultipliedAlpha false}))))

(defonce game {:context gl-context})

(defn data-uri->Uint8Array [data-uri]
  (let [base64-str (last (.split data-uri ","))]
    (js/Uint8Array.fromBase64 base64-str)))

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
                    (def hmm (.ConvertFileList ajs ajs-file-list "gltf2"))))))))))

  (def gltf-json
    (->> (.GetFile hmm 0)
         (.GetContent)
         (.decode (js/TextDecoder.))
         (js/JSON.parse)
         ((fn [json] (-> json (js->clj :keywordize-keys true))))))

  (def result-bin
    (->> (.GetFile hmm 1)
         (.GetContent)))

  (#_"all data (w/o images bc it's too big to print in repl)"
   -> gltf-json (dissoc :images))

  (#_"the texture byte array"
   -> gltf-json :images first :uri data-uri->Uint8Array type)

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
                  :signatures '{main ([] void)}
                  :functions  '{main ([] (= o_color (vec4 uv normal.y "0.2")))}}]
      {:esse-id :DEFAULT-GLTF-SHADER
       :program-data (shader/create-program game vert frag)}))

  (-> default-gltf-shader :program-data) 

  (def summons
    (gl-incantation game
                    [default-gltf-shader]
                    (gltf-magic gltf-json result-bin)))

  (let [width   (-> game :context .-canvas .-clientWidth)
        height  (-> game :context .-canvas .-clientHeight)
        indices (let [mesh        (some-> gltf-json :meshes first)
                      accessors   (some-> gltf-json :accessors)
                      indices     (some-> mesh :primitives first :indices)]
                  (get accessors indices))
        program (-> default-gltf-shader :program-data :program)
        uni-loc (-> default-gltf-shader :program-data :uni-locs)
        u_mvp   (get uni-loc 'u_mvp)
        vao     (nth (first summons) 2)
        id-mat4 (f32-arr (vec (mat/matrix44)))]

    (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT)))
    (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
    (gl game viewport 0 0 width height)

    (gl game useProgram program)
    (gl game bindVertexArray vao)
    (gl game uniformMatrix4fv u_mvp false id-mat4)
    (gl game drawElements
        (gl game TRIANGLES)
        (:count indices)
        (:componentType indices)
        0))

  :-)
