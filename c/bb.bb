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
  ([qualifier {:keys [single-header include
                      library header-class-name symbols-class-name]}]
   (io/make-parents "c/j/gen/x")
   (io/make-parents "c/j/classes/x")
   (println "jextracting [" qualifier "]" (or single-header include) "...")
   (b/process {:command-args
               (build-cmd
                [jextract-runner
                 "--output" "c/j/gen"
                 "-t" qualifier
                 (when library ["--library" library])
                 (when (not (str/blank? header-class-name))  ["--header-class-name" header-class-name])
                 (when (not (str/blank? symbols-class-name)) ["--symbols-class-name" symbols-class-name])
                 (or single-header
                     (list-header-files include))])})))

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
    (jextract qualifier {:single-header lib-path :library libname :header-class-name "parsl" :symbols-class-name "parsl_r"})
    (b/copy-file {:src out-path :target (str "resources/public/libs/" libname-o)})))

(defn build-box2d [& _]
  ;; we add -DBUILD_SHARED_LIBS=ON manually for now in build.sh 
  (b/process {:env {"PATH" (System/getenv "PATH")}
              :dir "../box2d"
              :command-args ["cmd" "/c" "build.sh"] :out :inherit})
  (let [box2d-home    "../box2d"
        box2d-shared  "box2dd.dll"
        box2d-include (str box2d-home "/include/box2d")
        box2d-o       (str box2d-home "/build/bin/debug/" box2d-shared)]
    (b/copy-file {:src    box2d-o
                  :target (str "resources/public/libs/" box2d-shared)})
    (jextract "box2d"
              {:include            box2d-include
               :header-class-name  "b2d"
               :symbols-class-name "b2d_r"})))

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
  (compile-gen-java))

(defn findc [& _]
  (b/process {:dir "c" :command-args ["gcc" "-H" "-fsyntax-only" "lib/par_streamlines.c"]}))

(defn runc [& _]
  (b/process {:command-args ["c/o/hello.exe"] :out :inherit}))

(def minusthree-dev-basis (delay (b/create-basis {:project "deps.edn" :aliases [:imgui :windows :native :repl :profile]})))

(defn min3 [& _]
  (println "running -3")
  (let [cmd (b/java-command {:basis @minusthree-dev-basis
                             :main  'clojure.main
                             :main-args ["-m" "minusthree.-dev.start"]})]
    (b/process cmd)))
