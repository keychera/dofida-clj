(ns playground
  (:require
   [minusone.rules.gl.gl :refer [GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT]]
   [minusone.rules.gl.gltf :as gltf]
   [minusone.rules.model.assimp-js :as assimp-js]
   [play-cljc.macros-js :refer-macros [gl]]))

(defonce canvas (js/document.querySelector "canvas"))
(defonce gl-context (.getContext canvas "webgl2" (clj->js {:premultipliedAlpha false})))
(defonce width (-> canvas .-clientWidth))
(defonce height (-> canvas .-clientHeight))
(defonce game {:context gl-context})

(assimp-js/then-load-model
 ["assets/simpleskin.gltf"]
 #_{:clj-kondo/ignore [:inline-def]}
 (fn [{:keys [gltf bins]}]
   (def gltf-json gltf)
   (def result-bin (first bins))))

(do (gl game clearColor 0.2 0.2 0.4 1.0)
    (gl game clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT)))

(:skins gltf-json)
(:nodes gltf-json)
(take 6 (:accessors gltf-json))
(:bufferViews gltf-json)
(-> gltf-json :meshes first)
(:buffers gltf-json)

(let [accessors    (-> gltf-json :accessors)
      buffer-views (-> gltf-json :bufferViews)
      skins        (-> gltf-json :skins first)
      ibm          (-> skins :inverseBindMatrices)
      accessor     (get accessors ibm)
      buffer-view  (get buffer-views (:bufferView accessor))
      byteLength   (:byteLength buffer-view)
      byteOffset   (:byteOffset buffer-view)
      ibm-uint8s   (.subarray result-bin byteOffset (+ byteLength byteOffset))
      ibm-f32s     (js/Float32Array. ibm-uint8s.buffer
                                     ibm-uint8s.byteOffset
                                     (/ ibm-uint8s.byteLength 4.0))
      nodes        (map-indexed (fn [idx node] (assoc node :idx idx)) (:nodes gltf-json))]

  [(/ (.-length ibm-f32s) 16) :done
   (nth nodes 0)
   (gltf/node->transform-db nodes)])
