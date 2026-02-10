(ns build
  (:require
   [clojure.tools.build.api :as b]))

(def game 'self.chera/dofida-clj)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def target-dir "target")
(def class-dir (str target-dir "/classes"))
(def uber-file (format "target/%s-%s.jar" (name game) version))

(def basis (delay (b/create-basis {:project "deps.edn" :aliases [:jvm]})))

(defn clean [& _]
  (println "cleaning target...")
  (b/delete {:path target-dir}))

(defn desktop [& _]
  ;; (compile-java)
  (println "running desktop game...")
  (let [cmd (b/java-command {:basis @basis
                             :main  'clojure.main
                             :main-args ["-m" "platform.start"]})]
    (b/process cmd)))

(defn repl [& _]
  ;; (compile-java-repl)
  (println "running desktop game with repl...")
  (let [cmd (b/java-command {:basis (b/create-basis {:project "deps.edn" :aliases [:jvm :repl :profile :imgui]})
                             :main  'clojure.main
                             :main-args ["-m" "start-dev"]})]
    (b/process cmd)))

(def minusthree-dev-basis (delay (b/create-basis {:project "deps.edn" :aliases [:jvm :imgui :repl :profile]})))
(def minusthree-rel-basis (delay (b/create-basis {:project "deps.edn" :aliases [:jvm :imgui]})))

(defn minusthree [& _]
  ;; (compile-java-repl)
  (println "running minusthree dev")
  (let [cmd (b/java-command {:basis @minusthree-dev-basis
                             :main  'clojure.main
                             :main-args ["-m" "minusthree.-dev.start"]})]
    (b/process cmd)))

(defn minusthree-rel [& _]
  ;; (compile-java-repl)
  (println "running minusthree release")
  (let [cmd (b/java-command {:basis @minusthree-rel-basis
                             :main  'clojure.main
                             :main-args ["-m" "minusthree.platform.jvm.jvm-game"]})]
    (b/process cmd)))

(defn minusthree-uber [& _]
  ;; (compile-java-repl)
  (println "compiling minusthree classes")
  (b/write-pom {:lib game
                :version version
                :basis @minusthree-rel-basis
                :src-dirs ["src"]
                :class-dir class-dir})
  (b/copy-dir {:src-dirs ["resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis @minusthree-rel-basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :ns-compile ['minusthree.platform.jvm.jvm-game]})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @minusthree-rel-basis
           :main 'minusthree.platform.jvm.jvm-game}))
