(ns minustwo.model.assimp-lwjgl
  (:require
   [cheshire.core :as json]
   [engine.macros :refer [public-resource-path]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gltf :as gltf]
   [minustwo.model.assimp :as assimp]
   [odoyle.rules :as o])
  (:import
   [org.lwjgl.assimp Assimp]))

;; https://javadoc.lwjgl.org/org/lwjgl/assimp/package-summary.html

(defn load-model [model-path export-format]
  (let [rs-path    (.resolve public-resource-path model-path)
        flags      (bit-or Assimp/aiProcess_Triangulate
                           Assimp/aiProcess_GenUVCoords
                           Assimp/aiProcess_JoinIdenticalVertices
                           Assimp/aiProcess_SortByPType)
        aiScene    (Assimp/aiImportFile (str rs-path) flags)
        _          (assert (some? aiScene) (str "aiScene for " model-path " is null!"))
        blob       (Assimp/aiExportSceneToBlob aiScene export-format 0)
        ;; we assume only 2 files for now, or rather, convention for ourself
        gltf-buf   (.data blob)
        gltf-json  (.toString (.decode java.nio.charset.StandardCharsets/UTF_8 gltf-buf))
        gltf       (json/parse-string gltf-json true)
        bin-blob   (.next blob)
        bin-buf    (.data bin-blob)
        parent-dir (.getParent rs-path)
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

(comment
  (require '[clojure.java.io :as io])
  (import [java.nio.file Files])
  (Files/exists (.resolve public-resource-path "assets/models/TopazAndNumby/Topaz.pmx") (into-array java.nio.file.LinkOption []))
  (io/input-stream (io/resource "public/assets/models/SilverWolf/银狼.pmx"))
  (let [model-path (.resolve public-resource-path "assets/models/SilverWolf/银狼.pmx") ;; kanji character fails to load somehow
        filename   "C:/Users/Kevin/Documents/projects/dofida-clj/resources/public/assets/models/SilverWolf/SilverWolf.pmx"
        flags      (bit-or Assimp/aiProcess_Triangulate
                           Assimp/aiProcess_GenUVCoords
                           Assimp/aiProcess_JoinIdenticalVertices
                           Assimp/aiProcess_SortByPType)
        aiScene    (Assimp/aiImportFile filename flags)
        _          (assert (some? aiScene) (str "aiScene for " model-path " is null!"))
        ;; _          (Assimp/aiExportScene aiScene "gltf2" "dummy.gltf" 0)
        blob       (Assimp/aiExportSceneToBlob aiScene "gltf2" 0)
        gltf-buf  (.data blob)
        gltf-json (.toString (.decode java.nio.charset.StandardCharsets/UTF_8 gltf-buf))
        gltf      (json/parse-string gltf-json true)
        gltf      (assoc-in gltf [:asset :dir] (.getParent model-path))
        bin-blob  (.next blob)
        bin-buf   (.data bin-blob)
        slice     (doto (.duplicate bin-buf) (.position 10) (.limit 20))]
    [(type bin-buf) (.slice slice) gltf (.getParent model-path)]))