(ns minusthree.engine.loader
  (:require
   [clojure.java.io :as io])
  (:import
   [java.io File]
   [java.nio.file Files Path]
   [java.nio.file.attribute FileAttribute]))

(defonce temp-dir
  (doto (Files/createTempDirectory "dofidalibs-" (into-array FileAttribute []))
    (-> Path/.toFile File/.deleteOnExit)))

(defn load-libs [libname]
  (let [obj      (str libname ".dll")
        path     (str "public/libs/" obj)
        o-res    (io/resource path)
        obj-path (.resolve temp-dir obj)
        obj-file (.toFile obj-path)]
    (println "loading" obj "...")
    (with-open [in  (io/input-stream o-res)
                out (io/output-stream obj-file)]
      (io/copy in out))
    (.deleteOnExit obj-file)
    (System/load (str (.toAbsolutePath obj-path)))))
