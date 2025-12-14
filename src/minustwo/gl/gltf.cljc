(ns minustwo.gl.gltf
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [engine.math :as m-ext :refer [decompose-matrix44]]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_ELEMENT_ARRAY_BUFFER]]
   [minustwo.utils :as utils]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.vector :as v]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.core :as g]
   [thi.ng.math.core :as m]
   [com.rpl.specter :as sp]))

(def gltf-type->num-of-component
  {"SCALAR" 1
   "VEC2"   2
   "VEC3"   3
   "VEC4"   4
   "MAT2"   4
   "MAT3"   9
   "MAT4"   16})

(s/def ::primitives sequential?)
(s/def ::joints vector?)
(s/def ::transform-tree (s/coll-of ::node+transform :kind vector))
(s/def ::inv-bind-mats vector?)

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

(defn primitive-spell [gltf-data result-bin use-shader]
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
          {:buffer-type GL_ELEMENT_ARRAY_BUFFER
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

(defn get-ibm-inv-mats [gltf-data result-bin]
  ;; assuming only one skin
  (when-let [ibm (some-> gltf-data :skins first :inverseBindMatrices)]
    (let [accessors     (some-> gltf-data :accessors)
          buffer-views  (some-> gltf-data :bufferViews)
          accessor      (get accessors ibm)
          buffer-view   (get buffer-views (:bufferView accessor))
          byteLength    (:byteLength buffer-view)
          byteOffset    (:byteOffset buffer-view)
          ^ints ibm-u8s #?(:clj  [result-bin byteLength byteOffset] ;; jvm TODO
                           :cljs (.subarray result-bin byteOffset (+ byteLength byteOffset)))
          ibm-f32s      #?(:clj  ibm-u8s ;; jvm TODO
                           :cljs (js/Float32Array. ibm-u8s.buffer
                                                   ibm-u8s.byteOffset
                                                   (/ ibm-u8s.byteLength 4.0)))
          inv-bind-mats (into [] (map (fn [i] (utils/f32s->get-mat4 ibm-f32s i))) (range (:count accessor)))]
      inv-bind-mats)))

(defonce debug-data* (atom {}))

(s/def :geom/matrix #(instance? thi.ng.geom.matrix.Matrix44 %))
(s/def :geom/translation #(instance? thi.ng.geom.vector.Vec3 %))
(s/def :geom/rotation #(instance? thi.ng.geom.quaternion.Quat4 %))
(s/def :geom/scale #(instance? thi.ng.geom.vector.Vec3 %))
(s/def ::node+transform (s/keys :req-un [:geom/matrix :geom/translation :geom/rotation :geom/scale]))

(s/def :array/matrix (s/coll-of number? :count 16))
(s/def :array/translation (s/coll-of number? :count 3))
(s/def :array/rotation (s/coll-of number? :count 4))
(s/def :array/scale (s/coll-of number? :count 3))
(s/def ::children (s/coll-of number?))
(s/def ::incomplete-node ;; I think I have incomplete assumption, currently this doesn't check anything, all node will be processed
  (s/or :only-matrix   (s/keys :req-un [:array/matrix])
        :only-trs      (s/keys :req-un [(or :array/translation :array/rotation :array/scale)])
        :only-children (s/keys :req-un [::children])))

(s/fdef process-as-geom-transform
  :args (s/cat :node ::incomplete-node)
  :ret ::node+transform)
(defn process-as-geom-transform
  "if node have :matrix, decompose it and attach :translation, :rotation, :scale
   if not, it assumed to have either :translation, :rotation, or :scale, 
   then create :matrix out of it"
  [node]
  (let [matrix (some-> (:matrix node) (mat/matrix44))]
    (if matrix
      (let [decom (decompose-matrix44 matrix)]
        (assoc node
               :matrix matrix
               :translation (:translation decom)
               :rotation (:rotation decom)
               :scale (:scale decom)))
      (let [trans     (some-> (:translation node) (v/vec3))
            trans-mat (some-> trans m-ext/translation-mat)
            rot       (some-> (:rotation node) (q/quat))
            rot-mat   (some-> rot (g/as-matrix))
            scale     (some-> (:scale node) (v/vec3))
            scale-mat (some-> scale (m-ext/vec3->scaling-mat))
            matrix    (transduce (filter some?) m-ext/mat44-mul-reducer [trans-mat rot-mat scale-mat])]
        (assoc node :matrix matrix
               :translation (or trans (v/vec3))
               :rotation (or rot (q/quat))
               :scale (or scale (v/vec3 1.0 1.0 1.0)))))))

(s/fdef node-transform-tree
  :args (s/cat :node (s/coll-of ::incomplete-node))
  :ret ::transform-tree)
(defn node-transform-tree [nodes]
  (let [tree (tree-seq :children
                       (fn [parent-node]
                         (into []
                               (comp
                                (map (fn [cid] (nth nodes cid)))
                                (map process-as-geom-transform))
                               (:children parent-node)))
                       (process-as-geom-transform (first nodes)))]
    ;; this somehow already returns an ordered seq, why? is it an optimization in the assimp part? is it the nature of DFS?
    (assert (apply <= (into [] (map :idx) tree)) "assumption broken: order of resulting seq is not the same as order of :idx")
    tree))

(defn gltf-spell
  "magic to pass to gl-magic/cast-spell"
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

      (eduction (primitive-spell gltf-data result-bin use-shader) primitives)

      {:insert-facts
       ;; assuming one skin for now
       (let [joints         (some-> gltf-data :skins first :joints)
             primitives     (into []
                                  (comp (map (fn [p] (select-keys p [:indices :tex-name :tex-unit :vao-name])))
                                        (map (fn [p] (update p :indices (fn [i] (get accessors i))))))
                                  primitives)
             nodes          (map-indexed (fn [idx node] (assoc node :idx idx)) (:nodes gltf-data))
             transform-tree (node-transform-tree nodes)]
         [[model-id ::primitives primitives]
          [model-id ::joints (or joints [])]
          [model-id ::transform-tree (vec transform-tree)]
          (let [inv-bind-mats (get-ibm-inv-mats gltf-data result-bin)]
            [model-id ::inv-bind-mats (or inv-bind-mats [])])])}])))

(defn calc-local-transform [{:keys [translation rotation scale] :as node}]
  (let [trans-mat    (m-ext/translation-mat translation)
        rot-mat      (g/as-matrix rotation)
        scale-mat    (m-ext/vec3->scaling-mat scale)
        local-trans  (reduce m/* [trans-mat rot-mat scale-mat])]
    (assoc node :local-transform local-trans)))

(s/fdef global-transform-tree
  :args (s/cat :transform-tree ::transform-tree))
(defn global-transform-tree [transform-tree]
  (tree-seq :children
            (fn [parent]
              (into [] (comp
                        (map (fn [cid] (calc-local-transform (nth transform-tree cid))))
                        (map (fn [{:keys [local-transform] :as node}]
                               (let [global-t (m/* (:global-transform parent) local-transform)]
                                 (assoc node :global-transform global-t)))))
                    (:children parent)))
            (-> (calc-local-transform (first transform-tree))
                ((fn [{:keys [local-transform] :as node}]
                   (assoc node :global-transform local-transform))))))

(defn create-joint-mats-arr [joints transform-tree inv-bind-mats]
  (let [f32s      (#?(:clj float-array :cljs #(js/Float32Array. %)) (* 16 (count joints)))
        global-tt (global-transform-tree transform-tree)]
    (doseq [[idx joint-id] (map-indexed vector joints)]
      (let [global-t   (:global-transform (nth global-tt joint-id))
            inv-bind-m (nth inv-bind-mats idx)
            joint-mat  (m/* global-t inv-bind-m)
            i          (* idx 16)]
        (dotimes [j 16]
          (aset f32s (+ i j) (nth joint-mat j)))))
    f32s))

(comment
  (require '[clojure.spec.test.alpha :as st]
           '[clojure.repl :refer [doc]])

  (let [node  {:children [21],
               :matrix [1 0 0 0 0 1 0 0 0 0 1 0 -0.5633741021156311 1.596090316772461 1.2539173364639282 1],
               :name "HairFB1_JNT"}]
    (s/explain ::incomplete-node node))

  (doc node-transform-tree)

  (st/instrument)
  (let [gltf-data (some-> @debug-data* :minustwo.stage.hidup/rubahperak :gltf-data)
        accessor  (some-> gltf-data :accessors)
        nodes     (some-> gltf-data :nodes)
        skins     (some-> gltf-data :skins)
        ibms      (some-> skins first :inverseBindMatrices)
        tree      (node-transform-tree nodes)]
    [(count tree)
     (-> skins first :joints count)
     (-> (get accessor ibms) :count)
     (take 4 tree)]
    ;; I was expecting the joints count = inverseBindMatrices count = (count (node-transform-tree nodes)) with filter ::incomplete-node
    ;; but I got is 440 = 440 != 439
    )

  (let [gltf-data (-> @debug-data* :minustwo.stage.hidup/rubahperak :gltf-data)
        nodes     (:nodes gltf-data)
        nodes     (assoc-in nodes [0 :children] [2 1 424]) ;; testing to break the order assumption
        nodes     (map-indexed (fn [idx node] (assoc node :idx idx)) nodes)]
    (node-transform-tree nodes))

  (let [gltf-data      (-> @debug-data* :minustwo.stage.hidup/rubahperak :gltf-data)
        result-bin     (-> @debug-data* :minustwo.stage.hidup/rubahperak :bin)
        nodes          (:nodes gltf-data)
        skin           (-> gltf-data :skins first)
        nodes          (map-indexed (fn [idx node] (assoc node :idx idx)) nodes)
        transform-tree (node-transform-tree nodes)
        inv-bind-mats  (get-ibm-inv-mats gltf-data result-bin)]
    [(reduce max (:joints skin))
     (:joints skin)
     (count inv-bind-mats)
     (count (:nodes gltf-data))
     (take 4 transform-tree)
     (->> (vec transform-tree)
          (sp/transform [sp/ALL #(#{"腰"} (:name %)) :rotation]
                        (fn [_] (q/quat-from-axis-angle
                                 (v/vec3 1.0 0.0 0.0)
                                 (m/radians 40)))))]

    #_(create-joint-mats-arr (:joints skin) transform-tree inv-bind-mats))

  :-)