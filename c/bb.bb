(ns bb
  (:require
   [clojure.tools.build.api :as b]
   [clojure.java.io :as io]))

;; https://foojay.io/today/project-panama-for-newbies-part-1/

(defn clean [& _]
  (b/delete {:path "c/j"})
  (b/delete {:path "c/o"}))

(defn c [& _]
  (io/make-parents "c/o/x")
  (println "compiling...")
  (b/process {:dir "c" :command-args ["gcc" "-o" "o/hello.exe" "s/hello.c"]}))

(defn findc [& _]
  (b/process {:dir "c" :command-args ["gcc" "-H" "-fsyntax-only" "s/hello.c"]}))

(defn jx [& _]
  (io/make-parents "c/j/gen/x")
  (io/make-parents "c/j/classes/x")
  (let [jextract "jextract.bat"]
    (println "jextracting...")
    (b/process {:dir "c" :command-args
                [jextract
                 "--output" "j/gen"
                 "-t" "org.unix"
                 "-I" "C:/msys64/ucrt64/include"
                 "C:/msys64/ucrt64/include/stdio.h"]})
    (println "jompiling...")
    (b/javac {:src-dirs ["c/j/gen"] :class-dir "c/j/classes"})))

(defn runc [& _]
  (b/process {:command-args ["c/o/hello.exe"] :out :inherit}))

(def minusthree-dev-basis (delay (b/create-basis {:project "deps.edn" :aliases [:imgui :windows :repl :profile]})))

(defn min3 [& _]
  (println "running -3")
  (let [cmd (b/java-command {:basis @minusthree-dev-basis
                             :main  'clojure.main
                             :main-args ["-m" "minusthree.-dev.start"]})]
    (b/process cmd)))
