(ns bb
  (:require
   [clojure.tools.build.api :as b]
   [clojure.java.io :as io]
   [clojure.string :as str]))

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

(defn jextract [qualifier libname]
  (let [lib-path (str "lib/" libname)]
    (io/make-parents "c/j/gen/x")
    (io/make-parents "c/j/classes/x")
    (println "jextracting [" qualifier "]" lib-path "...")
    (b/process {:dir "c"
                :command-args
                [jextract-runner
                 "--output" "j/gen"
                 "--library" (strip libname)
                 "-t" qualifier
                 lib-path]})))

(defn- build-par-streamlines [& _]
  (let [qualifier "par"
        libname "par_streamlines.c"
        lib-path (str "lib/" libname)
        out-path (str "o/" qualifier "/" (strip libname) ".dll")]
    (io/make-parents (str "c/" out-path))
    (println "charing" libname "...")
    (b/process {:dir "c" :command-args ["gcc" "-shared" "-o" out-path lib-path]})
    (jextract qualifier libname)))

(defn build-stdio [& _]
  (io/make-parents "c/j/gen/x")
  (io/make-parents "c/j/classes/x")
  (println "jextracting...")
  (b/process {:dir "c" :command-args [jextract-runner "--output" "j/gen" "-t" "org.unix" "-I" "C:/msys64/ucrt64/include" "C:/msys64/ucrt64/include/stdio.h"]}))

(defn compile-gen-java [& _]
  (println "jompiling...")
  (b/javac {:src-dirs ["c/j/gen"] :class-dir "c/j/classes"}))

(defn prep "build libs + jextract" [& _]
  (build-stdio)
  (build-par-streamlines)
  (compile-gen-java))

(defn findc [& _]
  (b/process {:dir "c" :command-args ["gcc" "-H" "-fsyntax-only" "lib/par_streamlines.c"]}))

(defn runc [& _]
  (b/process {:command-args ["c/o/hello.exe"] :out :inherit}))

(def minusthree-dev-basis (delay (b/create-basis {:project "deps.edn" :aliases [:imgui :windows :repl :profile]})))

(defn min3 [& _]
  (println "running -3")
  (let [cmd (b/java-command {:basis @minusthree-dev-basis
                             :main  'clojure.main
                             :main-args ["-m" "minusthree.-dev.start"]})]
    (b/process cmd)))
