(ns playground
  (:require
   [minusone.rules.model.assimp-js :as assimp-js]
   [play-cljc.macros-js :refer-macros [gl]])
  (:require-macros  
   [macros :refer [f32s->get-mat4]]))

(defonce canvas (js/document.querySelector "canvas"))
(defonce gl-context (.getContext canvas "webgl2" (clj->js {:premultipliedAlpha false})))
(defonce width (-> canvas .-clientWidth))
(defonce height (-> canvas .-clientHeight))
(defonce game {:context gl-context})

(assimp-js/then-load-model
 ["assets/models/TopazAndNumby/Topaz.pmx"]
 #_{:clj-kondo/ignore [:inline-def]}
 (fn [{:keys [gltf bins]}]
   (def gltf-json gltf)
   (def result-bin (first bins))))

(do (gl game clearColor 0.2 0.2 0.4 1.0)
    (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT))))

(:skins gltf-json)
(count (:nodes gltf-json))
(take 3 (:accessors gltf-json))

(let [accessors    (-> gltf-json :accessors)
      buffer-views (-> gltf-json :bufferViews)
      ibm          (-> gltf-json :skins first :inverseBindMatrices)
      accessor     (get accessors ibm)
      buffer-view  (get buffer-views (:bufferView accessor))
      byteLength   (:byteLength buffer-view)
      byteOffset   (:byteOffset buffer-view)
      ibm-uint8s   (.subarray result-bin byteOffset (+ byteLength byteOffset))
      ibm-f32s     (js/Float32Array. ibm-uint8s.buffer)]
  [(.-length ibm-f32s)
   (aget ibm-f32s 0)
   (f32s->get-mat4 ibm-f32s 0)])

