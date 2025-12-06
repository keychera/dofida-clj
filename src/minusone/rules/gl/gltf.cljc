(ns minusone.rules.gl.gltf
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [engine.math :as m-ext]
   [engine.utils :refer [f32s->get-mat4]]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.math.core :as m]))

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
(s/def ::inv-bind-mats vector?)
(s/def ::transform-db map?)

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

(defn primitive-incantation [gltf-json result-bin use-shader]
  (let [accessors   (some-> gltf-json :accessors)
        bufferViews (some-> gltf-json :bufferViews)]
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
                   :attr-size (gltf-type->size type)
                   :attr-type componentType
                   :offset (+ (:byteOffset bufferView) byteOffset)})))
         attributes)

        (let [id-accessor   (get accessors indices)
              id-bufferView (get bufferViews (:bufferView id-accessor))
              id-byteOffset (:byteOffset id-bufferView)
              id-byteLength (:byteLength id-bufferView)]
          {:bind-buffer "IBO"
           :buffer-type (gl-array-type :GL_ELEMENT_ARRAY_BUFFER)
           :buffer-data #?(:clj  [(count result-bin) id-byteOffset id-byteLength :jvm-not-handled]
                           :cljs (.subarray result-bin id-byteOffset (+ id-byteLength id-byteOffset)))})
        {:unbind-vao true}]))))

(defn process-image [model-id gltf-dir]
  (map-indexed
   (fn [idx image]
     (let [tex-name (str model-id "_image" idx)
           image    (cond-> image
                      (not (str/starts-with? (:uri image) "data:"))
                      (update :uri (fn [f] (str gltf-dir "/" f))))]
       (assoc image :image-idx idx :tex-name tex-name)))))

(defn get-ibm-inv-mats [gltf-json result-bin]
  ;; assuming only one skin
  (when-let [ibm (some-> gltf-json :skins first :inverseBindMatrices)]
    (let [accessors     (some-> gltf-json :accessors)
          buffer-views  (some-> gltf-json :bufferViews)
          accessor      (get accessors ibm)
          buffer-view   (get buffer-views (:bufferView accessor))
          byteLength    (:byteLength buffer-view)
          byteOffset    (:byteOffset buffer-view)
          ^ints ibm-u8s (.subarray result-bin byteOffset (+ byteLength byteOffset))
          ibm-f32s       #?(:clj  ibm-u8s ;; jvm TODO
                            :cljs (js/Float32Array. ibm-u8s.buffer
                                                    ibm-u8s.byteOffset
                                                    (/ ibm-u8s.byteLength 4.0)))
          inv-bind-mats (into [] (map (fn [i] (f32s->get-mat4 ibm-f32s i))) (range (:count accessor)))]
      inv-bind-mats)))

;; I don't like this somehow
(defn node->transform-db
  ([gltf-nodes] (node->transform-db gltf-nodes 0 -1 (volatile! {})))
  ([gltf-nodes idx parent-idx transform-db*]
   (let [node             (nth gltf-nodes idx)
         local-transform  (or (some-> (:matrix node) mat/matrix44)
                              (cond->> (mat/matrix44)
                                (:rotation node)
                                (m/* (g/as-matrix
                                      (let [[x y z w] (:rotation node)]
                                        (q/quat x y z w))))
                                (:translation node)
                                (m/* (let [[x y z] (:translation node)]
                                       (m-ext/translation-mat x y z)))))
         parent-transform (or (get-in @transform-db* [parent-idx :global-transform]) (mat/matrix44))
         global-transform (m/* parent-transform local-transform)
         children         (:children node)
         bone-name        (:name node)]
     (vswap! transform-db* assoc idx
             {:local-transform local-transform
              :global-transform global-transform
              :parent-idx parent-idx
              :children children
              :name bone-name})
     (when children
       (doseq [c-idx children]
         (node->transform-db gltf-nodes c-idx idx transform-db*)))
     @transform-db*)))

(defn gltf-magic
  "magic to pass to gl-incantation"
  [gltf-json result-bin {:keys [model-id use-shader tex-unit-offset]}]
  (let [gltf-dir   (some-> gltf-json :asset :dir)
        _          (assert gltf-dir "no parent dir data in [:asset :dir]")
        mesh       (some-> gltf-json :meshes first) ;; only handle one mesh for now
        materials  (some-> gltf-json :materials)
        textures   (some-> gltf-json :textures)
        images     (eduction
                    (process-image model-id gltf-dir)
                    (some-> gltf-json :images))
        primitives (eduction
                    (create-vao-names (str model-id "_" (:name mesh)))
                    (match-textures tex-unit-offset materials textures images)
                    (some-> mesh :primitives))]
    ;; assume one glb/gltf = one binary for the time being
    (flatten
     [{:buffer-data result-bin :buffer-type (gl-array-type :GL_ARRAY_BUFFER)}
      (eduction
       (map (fn [{:keys [image-idx tex-name] :as image}]
              {:bind-texture tex-name :image image :tex-unit (+ tex-unit-offset image-idx)}))
       images)

      (eduction (primitive-incantation gltf-json result-bin use-shader) primitives)

      {:insert-facts
       (let [primitives (mapv (fn [p] (select-keys p [:indices :tex-name :tex-unit :vao-name])) primitives)
             nodes      (map-indexed (fn [idx node] (assoc node :idx idx)) (:nodes gltf-json))]
         [[model-id ::primitives primitives]
          [model-id ::transform-db (node->transform-db nodes)]
          (when-let [inv-bind-mats (get-ibm-inv-mats gltf-json result-bin)]
            [model-id ::inv-bind-mats inv-bind-mats])])}])))

(defn create-joint-mats-arr ^floats [skin transform-db inv-bind-mats]
  (let [joints (:joints skin)
        f32s   (#?(:clj float-array :cljs #(js/Float32Array. %)) (* 16 (count joints)))]
    (doseq [[idx joint-id] (map-indexed vector joints)]
      (let [inv-bind-mat   (nth inv-bind-mats idx)
            joint          (get transform-db joint-id)
            joint-global-t (:global-transform joint)
            joint-mat      (m/* joint-global-t inv-bind-mat)
            i              (* idx 16)]
        (dotimes [j 16]
          (aset f32s (+ i j) (nth joint-mat j)))))
    f32s))
