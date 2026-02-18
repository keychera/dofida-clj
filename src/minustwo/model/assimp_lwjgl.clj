(ns minustwo.model.assimp-lwjgl
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [engine.utils :refer [get-public-resource get-parent-path]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gltf :as gltf]
   [minustwo.model.assimp :as assimp]
   [odoyle.rules :as o])
  (:import
   [java.nio ByteOrder]
   [org.lwjgl BufferUtils]
   [org.lwjgl.assimp Assimp]))

;; https://javadoc.lwjgl.org/org/lwjgl/assimp/package-summary.html

(defn load-model [model-path export-format]
  (let [model-res  (get-public-resource model-path)
        bytes      (.readAllBytes (io/input-stream model-res))
        buffer     (doto (BufferUtils/createByteBuffer (count bytes))
                     (.put bytes)
                     (.flip))
        flags      (bit-or Assimp/aiProcess_Triangulate
                           Assimp/aiProcess_GenUVCoords
                           Assimp/aiProcess_JoinIdenticalVertices
                           Assimp/aiProcess_SortByPType)
        aiScene    (Assimp/aiImportFileFromMemory buffer flags (str nil))
        _          (assert (some? aiScene) (str "aiScene for " model-path " is null!"))
        blob       (Assimp/aiExportSceneToBlob aiScene export-format 0)
        ;; we assume only 2 files for now, or rather, convention for ourself
        gltf-buf   (.data blob)
        gltf-json  (.toString (.decode java.nio.charset.StandardCharsets/UTF_8 gltf-buf))
        gltf       (json/parse-string gltf-json true)
        bin-blob   (.next blob)
        bin-buf    (.data bin-blob)
        _          (.order bin-buf ByteOrder/LITTLE_ENDIAN) ;; actually already little endian, but just to remind me this concept exist (slicing make big endian by default chatgpt says)
        parent-dir (get-parent-path model-path)
        gltf       (assoc-in gltf [:asset :dir] parent-dir)]
    [gltf bin-buf]))

(defn load-models-from-world*
  [models-to-load world*]
  (doseq [{:keys [model-files esse-id]} models-to-load]
    (println "[assimp-lwjgl] loading model" esse-id)
    (swap! world* o/retract esse-id ::assimp/model-to-load)
    (let [[gltf bin] (load-model (first model-files) "gltf2")]
      (println "[assimp-lwjgl] loaded" esse-id)
      (swap! world* o/insert esse-id {::gltf/data gltf ::gltf/bins [bin] ::gl-magic/casted? :pending}))))
