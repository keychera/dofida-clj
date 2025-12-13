(ns minustwo.gl.gltf
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_ELEMENT_ARRAY_BUFFER]]))

(def gltf-type->num-of-component
  {"SCALAR" 1
   "VEC2"   2
   "VEC3"   3
   "VEC4"   4
   "MAT2"   4
   "MAT3"   9
   "MAT4"   16})

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
           texture  (some->> tex-idx (get textures))
           image    (some->> texture :source (nth images))]
       (cond-> primitive
         image (assoc
                :tex-name (:tex-name image)
                :tex-unit (+ tex-unit-offset tex-idx)))))))

(defn primitive-incantation [gltf-data result-bin use-shader]
  (let [accessors   (some-> gltf-data :accessors)
        bufferViews (some-> gltf-data :bufferViews)]
    (map
     (fn [{:keys [vao-name attributes indices]}]
       [{:bind-vao vao-name}
        {:bind-current-buffer true}
        (eduction
         (map (fn [[attr-name accessor]]
                (merge {:attr-name attr-name}
                       (get accessors accessor))))
         (map (fn [{:keys [attr-name bufferView byteOffset componentType type]}]
                (let [bufferView (get bufferViews bufferView)]
                  {:point-attr (symbol attr-name)
                   :use-shader use-shader
                   :count (gltf-type->num-of-component type)
                   :component-type componentType
                   :offset (+ (:byteOffset bufferView) byteOffset)})))
         attributes)

        (let [id-accessor   (get accessors indices)
              id-bufferView (get bufferViews (:bufferView id-accessor))
              id-byteOffset (:byteOffset id-bufferView)
              id-byteLength (:byteLength id-bufferView)]
          {:bind-buffer "IBO"
           :buffer-type GL_ELEMENT_ARRAY_BUFFER
           :buffer-data #?(:clj  [(count result-bin) id-byteOffset id-byteLength :jvm-not-handled]
                           :cljs (.subarray result-bin id-byteOffset (+ id-byteLength id-byteOffset)))})
        {:unbind-vao true}]))))

(defn process-image-uri [model-id gltf-dir]
  (map-indexed
   (fn [idx image]
     (let [tex-name (str model-id "_image" idx)
           image    (cond-> image
                      (not (str/starts-with? (:uri image) "data:"))
                      (update :uri (fn [f] (str gltf-dir "/" f))))]
       (assoc image :image-idx idx :tex-name tex-name)))))

(defonce debug-data* (atom {}))

(defn gltf-spell
  "magic to pass to gl-incantation"
  [gltf-data result-bin {:keys [model-id use-shader tex-unit-offset]
                         :or {tex-unit-offset 0}}]
  (let [gltf-dir   (some-> gltf-data :asset :dir)
        _          (assert gltf-dir "no parent dir data in [:asset :dir]")
        mesh       (some-> gltf-data :meshes first) ;; only handle one mesh for now
        materials  (some-> gltf-data :materials)
        textures   (some-> gltf-data :textures)
        accessors  (some-> gltf-data :accessors)
        images     (eduction
                    (process-image-uri model-id gltf-dir)
                    (some-> gltf-data :images))
        primitives (eduction
                    (create-vao-names (str model-id "_" (:name mesh)))
                    (match-textures tex-unit-offset materials textures images)
                    (some-> mesh :primitives))]
    (swap! debug-data* assoc model-id {:gltf-data gltf-data :bin result-bin})
    ;; assume one glb/gltf = one binary for the time being
    (flatten
     [{:buffer-data result-bin :buffer-type GL_ARRAY_BUFFER}
      (eduction
       (map (fn [{:keys [image-idx tex-name] :as image}]
              {:bind-texture tex-name :image image :tex-unit (+ tex-unit-offset image-idx)}))
       images)

      (eduction (primitive-incantation gltf-data result-bin use-shader) primitives)

      {:insert-facts
       (let [primitives (into []
                              (comp (map (fn [p] (select-keys p [:indices :tex-name :tex-unit :vao-name])))
                                    (map (fn [p] (update p :indices (fn [i] (get accessors i))))))
                              primitives)]
         [[model-id ::primitives primitives]])}])))
