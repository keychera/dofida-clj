(ns build
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(def game 'self.chera/dofida-clj)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def target-dir "target")
(def class-dir (str target-dir "/input/classes"))
(def dist-dir (str target-dir "/output"))
(def base-uber-file (format "%s/jar/%s-%s.jar" dist-dir (name game) version))

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

(def minusthree-dev-basis (delay (b/create-basis {:project "deps.edn" :aliases [:imgui :repl :profile]})))
(def minusthree-rel-basis (delay (b/create-basis {:project "deps.edn" :aliases [:imgui]})))
(def minusthree-native-basis (delay (b/create-basis {:project "deps.edn" :aliases [:imgui :native]})))

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

(defn minusthree-compile
  ([] (minusthree-compile {:basis minusthree-rel-basis}))
  ([{:keys [basis]
     :or {basis minusthree-rel-basis}}]
   (println "compiling minusthree clj sources...")
   (b/compile-clj {:basis @basis
                   :src-dirs ["src"]
                   :class-dir class-dir
                   :ns-compile ['minusthree.platform.jvm.jvm-game]})))

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
  (minusthree-compile {:basis basis})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'minusthree.platform.jvm.jvm-game})
  (println "uberjar created at" uber-file)
  (println (str "run with `java -jar " uber-file "`")))

;; https://github.com/chirontt/lwjgl3-helloworld-native
;; https://github.com/libgdx/libgdx/issues/7113
;; still errs with
;; Exception in thread "main" com.oracle.svm.core.jdk.UnsupportedFeatureError: Classes cannot be defined at runtime by default 
;; when using ahead-of-time Native Image compilation. Tried to define class 'org/lwjgl/system/JNIBindingsImpl'
(defn minusthree-graal [& _]
  (let [rel-path   (str dist-dir "/rel")
        graal-exe  (str rel-path "/" (name game))
        graal-uber (format "%s/%s-%s-for-native.jar" rel-path (name game) version)
        graal-cmd  ["powershell" "/C" b/*project-root*
                    "native-image" "-jar" graal-uber
                    "-H:Name=minusthree"
                    "-H:+ReportExceptionStackTraces"
                    "-H:+ReportUnsupportedElementsAtRuntime"
                    "--features=clj_easy.graal_build_time.InitClojureClasses"
                    "--verbose"
                    "--no-fallback"
                    "--initialize-at-build-time=com.fasterxml.jackson"
                    "--initialize-at-run-time=org.lwjgl"
                    "-o" graal-exe]]

    (minusthree-uber {:uber-file graal-uber
                      :basis minusthree-native-basis})

    #_(let [native-jars   (into []
                              (filter #(re-find #"lwjgl-.*3.4.0-natives-windows.*\.jar$" %))
                              (:classpath-roots @minusthree-native-basis))
          meta-inf      (str rel-path "/META-INF")
          platform-root (str rel-path "/windows")
          natives-root  (str rel-path "/natives")]
      (print "gathering native deps...")
      (doseq [jar native-jars]
        (b/unzip {:zip-file   jar
                  :target-dir rel-path}))
      (b/delete {:path meta-inf})
      (doseq [file (file-seq (io/file (str rel-path "/windows")))]
        (when (str/ends-with? (.getName file) ".dll")
          (b/copy-file {:src    (.getAbsolutePath file)
                        :target (str natives-root "/" (.getName file))})))
      (b/delete {:path platform-root})
      (println "gathered at" natives-root))

    (println "running" (str/join " " (into [] (map #(if (> (count %) 64) (str (subs % 0 64) "...") %))  graal-cmd)))
    (io/make-parents graal-exe)
    (b/process {:command-args graal-cmd})))
