(ns minusone.rules.model.assimp-js
  (:require
   ["assimpjs" :as assimpjs]
   [engine.macros :refer [vars->map]]
   [engine.world :as world]
   [minusone.rules.gl.texture :as texture]
   [minusone.rules.model.assimp :as assimp]
   [odoyle.rules :as o]))

(defn data-uri->header+Uint8Array [data-uri]
  (let [[header base64-str] (.split data-uri ",")]
    [header (js/Uint8Array.fromBase64 base64-str)]))

;; following this on loading image to bindTexture with blob
;; https://webglfundamentals.org/webgl/lessons/webgl-qna-how-to-load-images-in-the-background-with-no-jank.html
(defn data-uri->ImageBitmap 
  "parse data-uri and pass the resulting bitmap to callback.
   callback will receive {:keys [bitmap width height]}"
  [data-uri callback]
  (let [[header uint8-arr] (data-uri->header+Uint8Array data-uri)
        data-type          (second (re-matches #".*:(.*);.*" header))
        blob               (js/Blob. #js [uint8-arr] #js {:type data-type})]
    (.then (js/createImageBitmap blob)
           (fn [bitmap]
             (let [width  (.-width bitmap)
                   height (.-height bitmap)]
               (println "blob:" data-type "count" (.-length uint8-arr))
               (callback (vars->map bitmap width height)))))))

(def gltf-type->size
  {"SCALAR" 1
   "VEC2"   2
   "VEC3"   3
   "VEC4"   4
   "MAT2"   4
   "MAT3"   9
   "MAT4"   16})

(def gl-array-type {:GL_ARRAY_BUFFER 34962 :GL_ELEMENT_ARRAY_BUFFER 34963})

(defn gltf-magic [gltf-json result-bin]
  (let [mesh        (some-> gltf-json :meshes first)
        accessors   (some-> gltf-json :accessors)
        bufferViews (some-> gltf-json :bufferViews)
        attributes  (some-> mesh :primitives first :attributes)
        indices     (some-> mesh :primitives first :indices)]
    (flatten
     [{:bind-buffer (:name mesh) :buffer-data result-bin :buffer-type (gl-array-type :GL_ARRAY_BUFFER)}
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
         :buffer-type (gl-array-type :GL_ELEMENT_ARRAY_BUFFER)})

      {:unbind-vao true}])))

;; https://shadow-cljs.github.io/docs/UsersGuide.html#infer-externs
;; drop core.async, error msg are not nice there
(defn then-load-model
  "promise to load model and pass the result via .then callback.
   callback will receive {:keys [gltf bins]}"
  [files callback]
  (.then (assimpjs)
         (fn [ajs]
           (.then (->> files
                       (map (fn [f] (-> f js/fetch (.then (fn [res] (.arrayBuffer res))))))
                       js/Promise.all)
                  (fn [arr-bufs]
                    (let [ajs-file-list (new (.-FileList ajs))]
                      (dotimes [i (count files)]
                        (.AddFile ^js ajs-file-list (nth files i) (js/Uint8Array. (aget arr-bufs i))))
                      (let [result (.ConvertFileList ^js ajs ajs-file-list "gltf2")
                            gltf   (->> (.GetFile ^js result 0)
                                        (.GetContent)
                                        (.decode (js/TextDecoder.))
                                        (js/JSON.parse)
                                        ((fn [json] (-> json (js->clj :keywordize-keys true)))))
                                                          ;; only handle one binary for now, (TODO use .FileCount)
                            bins   [(->> (.GetFile result 1) (.GetContent))]]
                        (callback (vars->map gltf bins)))))))))

(def rules
  (o/ruleset
   {::load-with-assimpjs
    [:what
     [esse-id ::assimp/model-to-load model-files]]

    ::gl-texture-to-load
    [:what
     [esse-id ::assimp/gltf gltf-json]]}))

(defn load-models-from-world*
  "load models from world* and fire callback for each models loaded.
   this will retract the ::assimp/model-to-load facts"
  [world*]
  (doseq [model-to-load (o/query-all @world* ::load-with-assimpjs)]
    (let [model-files (:model-files model-to-load)
          esse-id     (:esse-id model-to-load)]
      (swap! world* o/retract esse-id ::assimp/model-to-load)
      (then-load-model
       model-files
       (fn [{:keys [gltf bins]}]
         (println "[assimp-js] loaded" esse-id)
         (swap! world* o/insert esse-id {::assimp/gltf gltf ::assimp/bins bins}))))))

(defn load-texture-to-world*
  [world* game]
  (doseq [texture-to-shove (o/query-all @world* ::gl-texture-to-load)]
    (let [gltf-json (:gltf-json texture-to-shove)
          esse-id   (:esse-id texture-to-shove)
          ;; only one image for now
          data-uri  (some-> gltf-json :images first :uri)]
      (swap! world* o/retract esse-id ::gl-texture-to-load)
      (some-> data-uri
        (data-uri->ImageBitmap 
         (fn [{:keys [bitmap width height]}]
           (let [tex-data (texture/texture-incantation game bitmap width height 0)]
             (swap! world* o/insert esse-id ::texture/data tex-data))))))))

(def system
  {::world/rules rules})

;; REPL playground starts here
