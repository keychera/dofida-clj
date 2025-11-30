(ns minusone.rules.gl.gltf
  (:require
   [clojure.string :as str]))

(def gltf-type->size
  {"SCALAR" 1
   "VEC2"   2
   "VEC3"   3
   "VEC4"   4
   "MAT2"   4
   "MAT3"   9
   "MAT4"   16})

(def gl-array-type {:GL_ARRAY_BUFFER 34962 :GL_ELEMENT_ARRAY_BUFFER 34963})

(defn gltf-magic
  "magic to pass to gl-incantation"
  [gltf-json result-bin {:keys [from-shader tex-unit-offset]}]
  (let [gltf-dir    (some-> gltf-json :asset :dir)
        mesh        (some-> gltf-json :meshes first)
        accessors   (some-> gltf-json :accessors)
        bufferViews (some-> gltf-json :bufferViews)
        materials   (some-> gltf-json :materials)
        textures    (some-> gltf-json :textures)
        images      (some-> gltf-json :images)
        primitives  (some-> mesh :primitives)]
    (eduction
     (map-indexed
      (fn [idx {:keys [attributes indices material]}]
        (let [vao-name (str "vao" (.padStart (str idx) 4 "0") "_" (:name mesh))]
          (->> (flatten
                [;; assume one glb/gltf = one binary for the time being 
                 {:bind-buffer "the-binary" :buffer-data result-bin :buffer-type (gl-array-type :GL_ARRAY_BUFFER)}
                 {:bind-vao vao-name}

                 (eduction
                  (map (fn [[attr-name accessor]]
                         (merge {:attr-name attr-name}
                                (get accessors accessor))))
                  (map (fn [{:keys [attr-name bufferView byteOffset componentType type]}]
                         (let [bufferView (get bufferViews bufferView)]
                           {:point-attr (symbol attr-name)
                            :from-shader from-shader
                            :attr-size (gltf-type->size type)
                            :attr-type componentType
                            :offset (+ (:byteOffset bufferView) byteOffset)})))
                  attributes)

                 (let [id-accessor   (get accessors indices)
                       id-bufferView (get bufferViews (:bufferView id-accessor))
                       id-byteOffset (:byteOffset id-bufferView)
                       id-byteLength (:byteLength id-bufferView)]
                   {:bind-buffer "IBO"
                    :buffer-data (.subarray result-bin id-byteOffset (+ id-byteLength id-byteOffset))
                    :buffer-type (gl-array-type :GL_ELEMENT_ARRAY_BUFFER)})

                 ;; assume one primitive = one material = one image texture for the time being
                 ;; if multiple primitive uses the same image, it will produce the same fact
                 (let [material (get materials material)
                       tex-idx  (some-> material :pbrMetallicRoughness :baseColorTexture :index)
                       texture  (get textures tex-idx)
                       image    (get images (:source texture))
                       image    (cond-> image
                                  (not (str/starts-with? (:uri image) "data:"))
                                  (update :uri (fn [f] (str gltf-dir "/" f))))]
                   {:bind-texture (str "tex" tex-idx)
                    :image        image
                    :tex-unit     (+ tex-unit-offset tex-idx)})

                 {:unbind-vao true}])
               (into [])))))
     primitives)))