(ns minusone.rules.gl.gltf
  (:require
   [clojure.string :as str]
   [clojure.spec.alpha :as s]))

(def gltf-type->size
  {"SCALAR" 1
   "VEC2"   2
   "VEC3"   3
   "VEC4"   4
   "MAT2"   4
   "MAT3"   9
   "MAT4"   16})

(def gl-array-type {:GL_ARRAY_BUFFER 34962 :GL_ELEMENT_ARRAY_BUFFER 34963})

(s/def ::primitives sequential?)

(defn create-vao-names [prefix]
  (map-indexed
   (fn [idx primitive]
     (let [vao-name #?(:clj  (format "%s_vao%04d" idx prefix)
                       :cljs (str prefix "_vao" (.padStart (str idx) 4 "0")))]
       (assoc primitive :vao-name vao-name)))))

(defn match-textures [tex-unit-offset materials textures images]
  (map
   (fn [{:keys [material] :as primitive}]
     (let [material (get materials material)
           tex-idx  (some-> material :pbrMetallicRoughness :baseColorTexture :index)
           texture  (get textures tex-idx)
           image    (nth images (:source texture))]
       (assoc primitive
              :tex-name (:tex-name image)
              :tex-unit (+ tex-unit-offset tex-idx))))))

(defn primitive-incantation [gltf-json result-bin use-shader]
  (let [accessors   (some-> gltf-json :accessors)
        bufferViews (some-> gltf-json :bufferViews)]
    (map
     (fn [{:keys [vao-name attributes indices]}]
       [{:bind-vao vao-name}

        (eduction
         (map (fn [[attr-name accessor]]
                (merge {:attr-name attr-name}
                       (get accessors accessor))))
         (map (fn [{:keys [attr-name bufferView byteOffset componentType type]}]
                (let [bufferView (get bufferViews bufferView)]
                  {:point-attr (symbol attr-name)
                   :use-shader use-shader
                   :attr-size (gltf-type->size type)
                   :attr-type componentType
                   :offset (+ (:byteOffset bufferView) byteOffset)})))
         attributes)

        (let [id-accessor   (get accessors indices)
              id-bufferView (get bufferViews (:bufferView id-accessor))
              id-byteOffset (:byteOffset id-bufferView)
              id-byteLength (:byteLength id-bufferView)]
          {:bind-buffer "IBO"
           :buffer-data #?(:clj  [(count result-bin) id-byteOffset id-byteLength :jvm-not-handled]
                           :cljs (.subarray result-bin id-byteOffset (+ id-byteLength id-byteOffset)))
           :buffer-type (gl-array-type :GL_ELEMENT_ARRAY_BUFFER)})
        {:unbind-vao true}]))))

(defn process-image [gltf-dir]
  (map-indexed
   (fn [idx image]
     (let [tex-name (str "image" idx)
           image    (cond-> image
                      (not (str/starts-with? (:uri image) "data:"))
                      (update :uri (fn [f] (str gltf-dir "/" f))))]
       (assoc image :image-idx idx :tex-name tex-name)))))

(defn gltf-magic
  "magic to pass to gl-incantation"
  [gltf-json result-bin {:keys [model-id use-shader tex-unit-offset]}]
  (let [gltf-dir   (some-> gltf-json :asset :dir)
        _          (assert gltf-dir "no parent dir data in [:asset :dir]")
        mesh       (some-> gltf-json :meshes first) ;; only handle one mesh for now
        materials  (some-> gltf-json :materials)
        textures   (some-> gltf-json :textures)
        images     (eduction
                    (process-image gltf-dir)
                    (some-> gltf-json :images))
        primitives (eduction
                    (create-vao-names (:name mesh))
                    (match-textures tex-unit-offset materials textures images)
                    (some-> mesh :primitives))]
    ;; assume one glb/gltf = one binary for the time being
    (flatten
     [{:bind-buffer "the-binary" :buffer-data result-bin :buffer-type (gl-array-type :GL_ARRAY_BUFFER)}

      (eduction
       (map (fn [{:keys [image-idx tex-name] :as image}]
              {:bind-texture tex-name :image image :tex-unit (+ tex-unit-offset image-idx)}))
       images)

      (eduction (primitive-incantation gltf-json result-bin use-shader) primitives)

      {:insert-facts [[model-id ::primitives 
                       (mapv (fn [p] (select-keys p [:indices :tex-name :tex-unit :vao-name])) primitives)]]}])))
