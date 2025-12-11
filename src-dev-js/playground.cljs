(ns playground
  (:require
   [minustwo.gl.gl :refer [GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT gl]]
   [minusone.rules.gl.gltf :as gltf]
   [minusone.rules.model.assimp-js :as assimp-js]))

(defonce canvas (js/document.querySelector "canvas"))
(defonce gl-context (.getContext canvas "webgl2" (clj->js {:premultipliedAlpha false})))
(defonce width (-> canvas .-clientWidth))
(defonce height (-> canvas .-clientHeight))
(defonce ctx {:context gl-context})

(assimp-js/then-load-model
 ["assets/simpleskin.gltf"]
 #_{:clj-kondo/ignore [:inline-def]}
 (fn [{:keys [gltf bins]}]
   (def gltf-data gltf)
   (def result-bin (first bins))))

(do (gl ctx clearColor 0.2 0.2 0.4 1.0)
    (gl ctx clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT)))

(:skins gltf-data)
(:nodes gltf-data)
(take 6 (:accessors gltf-data))
(:bufferViews gltf-data)
(-> gltf-data :meshes first)
(:buffers gltf-data)

(let [accessors    (-> gltf-data :accessors)
      buffer-views (-> gltf-data :bufferViews)
      skins        (-> gltf-data :skins first)
      ibm          (-> skins :inverseBindMatrices)
      accessor     (get accessors ibm)
      buffer-view  (get buffer-views (:bufferView accessor))
      byteLength   (:byteLength buffer-view)
      byteOffset   (:byteOffset buffer-view)
      ibm-uint8s   (.subarray result-bin byteOffset (+ byteLength byteOffset))
      ibm-f32s     (js/Float32Array. ibm-uint8s.buffer
                                     ibm-uint8s.byteOffset
                                     (/ ibm-uint8s.byteLength 4.0))
      nodes        (map-indexed (fn [idx node] (assoc node :idx idx)) (:nodes gltf-data))]

  [(/ (.-length ibm-f32s) 16) :done
   (nth nodes 0)
   (gltf/node->transform-db nodes)])
