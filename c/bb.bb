(ns bb
  (:require
   [clojure.tools.build.api :as b]
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   [java.io File]))

;; https://foojay.io/today/project-panama-for-newbies-part-1/

(defn clean "clean" [& _]
  (b/delete {:path "c/j"})
  (b/delete {:path "c/o"}))

(defn c [& _]
  (io/make-parents "c/o/x")
  (println "compiling...")
  (b/process {:dir "c" :command-args ["gcc" "-o" "o/hello.exe" "s/hello.c"]}))

(defn strip [aname]
  (-> (first (str/split aname #"\."))
      (str/replace  #"[\.-]" "_")))

(def jextract-runner "jextract.bat")

(defn build-cmd [cmd-coll]
  (into [] (remove nil?) (flatten cmd-coll)))

(defn list-header-files [dirpath]
  (into []
        (comp (filter File/.isFile)
              (map File/.getPath)
              (filter #(= ".h" (subs % (- (count %) 2)))))
        (file-seq (io/file dirpath))))

(comment
  (list-header-files "../box2d/include/box2d"))

(defn jextract
  ([qualifier] (jextract qualifier {}))
  ([qualifier {:keys [single-header include-dir
                      library header-class-name symbols-class-name]}]
   (io/make-parents "c/j/gen/x")
   (io/make-parents "c/j/classes/x")
   (println "jextracting [" qualifier "]" (or single-header include-dir) "...")
   (b/process {:command-args
               (build-cmd
                [jextract-runner
                 "--output" "c/j/gen"
                 "-t" qualifier
                 (when library ["--library" library])
                 (when (not (str/blank? header-class-name))  ["--header-class-name" header-class-name])
                 (when (not (str/blank? symbols-class-name)) ["--symbols-class-name" symbols-class-name])
                 (or single-header
                     (list-header-files include-dir))])})))

(defn- build-par-streamlines [& _]
  (let [qualifier "par"
        libsource "par_streamlines.c"
        libname   (strip libsource)
        libname-o (str libname ".dll")
        lib-path  (str "c/lib/" libsource)
        out-path  (str "c/o/" qualifier "/" libname-o)]
    (io/make-parents out-path)
    (println "charing" libsource "...")
    (b/process {:command-args ["gcc" "-shared" "-o" out-path lib-path]})
    (b/copy-file {:src out-path :target (str "resources/public/libs/" libname-o)})
    (jextract qualifier {:single-header lib-path :library libname :header-class-name "parsl"})))

(defn build-box2d [& _]
  (let [box2d-home    "../box2d"
        box2d-shared  "box2dd.dll"
        box2d-include (str box2d-home "/include/box2d")
        box2d-o       (str box2d-home "/build/bin/debug/" box2d-shared)]
    ;; we add -DBUILD_SHARED_LIBS=ON manually for now in build.sh  
    (b/process {:dir box2d-home :command-args ["cmd" "/c" "build.sh"] :out :inherit})
    (b/copy-file {:src    box2d-o
                  :target (str "resources/public/libs/" box2d-shared)})
    (jextract "box2d"
              {:include-dir       box2d-include
               :library           "box2dd"
               :header-class-name "b2d"})))

(defn build-thorvg [& _]
  (let [out           "c/o/thorvg"
        builddir      "builddir"
        thorvg-home   "../thorvg"
        thorvg-shared "libthorvg-1.dll"
        thorvg-capi   (str out "/include/thorvg-1/thorvg_capi.h")
        out-dir       (.getAbsolutePath (io/file out))]
    (io/make-parents out)
    (b/process {:dir thorvg-home :command-args ["meson" "setup" "--reconfigure" (str "--prefix=" out-dir) builddir "-Dbindings=\"capi\""] :out :inherit})
    (b/process {:dir thorvg-home :command-args ["ninja" "-C" builddir "install"] :out :inherit})
    (b/copy-file {:src (str out "/bin/libthorvg-1.dll")
                  :target (str "resources/public/libs/" thorvg-shared)})
    (jextract "thorvg"
              {:single-header     thorvg-capi
               :library           "libthorvg-1"
               :header-class-name "tvg"})))

(defn build-stdio [& _]
  (io/make-parents "c/j/gen/x")
  (io/make-parents "c/j/classes/x")
  (println "jextracting...")
  (b/process {:dir "c" :command-args [jextract-runner "--output" "j/gen" "-t" "org.unix" "-I" "C:/msys64/ucrt64/include" "C:/msys64/ucrt64/include/stdio.h"]}))

(defn compile-gen-java [& _]
  (println "jompiling...")
  (b/javac {:src-dirs ["c/j/gen"] :class-dir "c/j/classes"}))

(defn prep "build libs + jextract" [& _]
  (build-par-streamlines)
  (build-box2d)
  (build-thorvg)
  (compile-gen-java))

;; e.g. bb -x bb/findc :target .\c\lib\par_streamlines.c
(defn findc [& {:keys [target]}]
  (b/process {:command-args ["gcc" "-H" "-fsyntax-only" target]}))

(defn runc [& _]
  (b/process {:command-args ["c/o/hello.exe"] :out :inherit}))

(def minusthree-dev-basis (delay (b/create-basis {:project "deps.edn" :aliases [:imgui :windows :native :repl :profile]})))

(defn min3 [& _]
  (println "running -3")
  (let [cmd (b/java-command {:basis @minusthree-dev-basis
                             :main  'clojure.main
                             :main-args ["-m" "minusthree.-dev.start"]})]
    (b/process cmd)))
