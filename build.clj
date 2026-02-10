(ns build
  (:require
   [clojure.tools.build.api :as b]))

(def game 'self.chera/dofida-clj)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def target-dir "target")
(def class-dir (str target-dir "/classes"))
(def base-uber-file (format "target/%s-%s.jar" (name game) version))

(def basis (delay (b/create-basis {:project "deps.edn" :aliases [:jvm]})))

(defn clean [& _]
  (println "cleaning target...")
  (b/delete {:path target-dir}))

(defn desktop [& _]
  (println "running desktop game...")
  (let [cmd (b/java-command {:basis @basis
                             :main  'clojure.main
                             :main-args ["-m" "platform.start"]})]
    (b/process cmd)))

(defn repl [& _]
  (println "running desktop game with repl...")
  (let [cmd (b/java-command {:basis (b/create-basis {:project "deps.edn" :aliases [:jvm :repl :profile :imgui]})
                             :main  'clojure.main
                             :main-args ["-m" "start-dev"]})]
    (b/process cmd)))

(def minusthree-dev-basis (delay (b/create-basis {:project "deps.edn" :aliases [:jvm :imgui :repl :profile]})))
(def minusthree-rel-basis (delay (b/create-basis {:project "deps.edn" :aliases [:jvm :imgui]})))
(def minusthree-native-basis (delay (b/create-basis {:project "deps.edn" :aliases [:jvm :imgui :native]})))

(defn minusthree [& _]
  (println "running minusthree dev")
  (let [cmd (b/java-command {:basis @minusthree-dev-basis
                             :main  'clojure.main
                             :main-args ["-m" "minusthree.-dev.start"]})]
    (b/process cmd)))

(defn minusthree-rel [& _]
  (println "running minusthree release")
  (let [cmd (b/java-command {:basis @minusthree-rel-basis
                             :main  'clojure.main
                             :main-args ["-m" "minusthree.platform.jvm.jvm-game"]})]
    (b/process cmd)))

(defn minusthree-uber
  [{:keys [basis uber-file]
    :or {uber-file base-uber-file
         basis minusthree-rel-basis}}]
  (println "making an uberjar...")
  (b/write-pom {:lib game
                :version version
                :basis @basis
                :src-dirs ["src"]
                :class-dir class-dir})
  (b/copy-dir {:src-dirs ["resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :ns-compile ['minusthree.platform.jvm.jvm-game]})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'minusthree.platform.jvm.jvm-game})
  (println "uberjar created at" uber-file)
  (println (str "run with `java -jar " uber-file "`")))

(defn minusthree-graal [& _]
  (let [graal-uber (format "target/%s-%s-for-native.jar" (name game) version)]
    (minusthree-uber {:uber-file graal-uber
                      :basis minusthree-native-basis})))
