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

(defn build-cmd [cmd-coll]
  (into [] (remove nil?) (flatten cmd-coll)))

(defn jextract
  ([qualifier lib-path] (jextract qualifier lib-path {}))
  ([qualifier lib-path {:keys [library header-class-name symbols-class-name]}]
   (io/make-parents "c/j/gen/x")
   (io/make-parents "c/j/classes/x")
   (println "jextracting [" qualifier "]" lib-path "...")
   (b/process {:dir "c"
               :command-args
               (build-cmd
                [jextract-runner
                 "--output" "j/gen"
                 "-t" qualifier
                 (when library ["--library" library])
                 (when (not (str/blank? header-class-name))  ["--header-class-name" header-class-name])
                 (when (not (str/blank? symbols-class-name)) ["--symbols-class-name" symbols-class-name])
                 lib-path])})))

(defn- build-par-streamlines [& _]
  (let [qualifier "par"
        libsource "par_streamlines.c"
        libname   (strip libsource)
        libname-o (str libname ".dll")
        lib-path  (str "lib/" libsource)
        out-path  (str "o/" qualifier "/" libname-o)
        abs-out   (str "c/" out-path)]
    (io/make-parents abs-out)
    (println "charing" libsource "...")
    (b/process {:dir          "c"
                :command-args ["gcc" "-shared" "-o" out-path lib-path]})
    (jextract qualifier lib-path {:library libname :header-class-name "parsl" :symbols-class-name "parsl_r"})
    (b/copy-file {:src    abs-out
                  :target (str "resources/public/libs/" libname-o)})))

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

(def minusthree-dev-basis (delay (b/create-basis {:project "deps.edn" :aliases [:imgui :windows :native :repl :profile]})))

(defn min3 [& _]
  (println "running -3")
  (let [cmd (b/java-command {:basis @minusthree-dev-basis
                             :main  'clojure.main
                             :main-args ["-m" "minusthree.-dev.start"]})]
    (b/process cmd)))
