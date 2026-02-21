(ns minusthree.engine.loader 
  (:require
   [clojure.java.io :as io]) 
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]))

(defn load-libs [libname]
  (let [obj      (str libname ".dll")
        path     (str "public/libs/" obj)
        o-res    (io/resource path)
        temp-dir (Files/createTempDirectory "dofidalibs-" (into-array FileAttribute []))
        obj-path (.resolve temp-dir obj)
        obj-file (.toFile obj-path)]
    (println "loading" obj "...")
    (with-open [in  (io/input-stream o-res)
                out (io/output-stream obj-file)]
      (io/copy in out))
    (.deleteOnExit obj-file)
    (System/load (str (.toAbsolutePath obj-path)))))
