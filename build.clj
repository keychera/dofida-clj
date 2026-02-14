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
(def rel-dir (str dist-dir "/rel"))
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

(def minusthree-dev-basis (delay (b/create-basis {:project "deps.edn" :aliases [:imgui :windows :repl :profile]})))
(def minusthree-rel-basis (delay (b/create-basis {:project "deps.edn" :aliases [:imgui :windows]})))

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

(def os   #{"windows" "linux" "macos"})
(def arch #{"amd64" "arm64"})

;; will hammock more of this multi os target
(defn ms-windows? []
  (.startsWith (System/getProperty "os.name") "Windows"))

(defn minusthree-prepare-for-graal
  [& _]
  (let [os-alias  (if (ms-windows?) :windows :linux)
        basis     (delay (b/create-basis {:project "deps.edn" :aliases [:imgui :native os-alias]}))
        uber-file (format "%s/%s-%s-for-native.jar" rel-dir (name game) version)]
    (minusthree-uber {:uber-file uber-file :basis basis})
    (b/process {:command-args ["java"
                               "-agentlib:native-image-agent=caller-filter-file=resources/META-INF/native-image/filter.json,config-output-dir=resources/META-INF/native-image"
                               "-jar" uber-file]})))

;; https://github.com/chirontt/lwjgl3-helloworld-native
;; still errs with
;; Exception in thread "main" com.oracle.svm.core.jdk.UnsupportedFeatureError: Classes cannot be defined at runtime by default 
;; when using ahead-of-time Native Image compilation. Tried to define class 'org/lwjgl/system/JNIBindingsImpl'
;; answer: drop to lwjgl 3.3.6, do https://github.com/clj-easy/graal-docs/blob/master/README.adoc#automatically-discovering-reflection-config

(defn minusthree-graal
  [& _]
  (let [os         (if (ms-windows?) "windows" "linux")
        arch       (System/getProperty "os.arch")
        os-arch    (str os "-" arch)
        graal-bin  (format "%s/%s-%s-%s" rel-dir (name game) version os-arch)
        os-alias  (if (ms-windows?) :windows :linux)
        basis      (delay (b/create-basis {:project "deps.edn" :aliases [:imgui :native os-alias]}))
        graal-uber (format "%s/%s-%s-for-native.jar" rel-dir (name game) version)
        graal-cmd  [(if (ms-windows?) "native-image.cmd" "native-image") "-jar" graal-uber
                    "-H:+ReportExceptionStackTraces"
                    "-H:+ReportUnsupportedElementsAtRuntime"
                    "--features=clj_easy.graal_build_time.InitClojureClasses"
                    "--verbose"
                    "--no-fallback"
                    (str "--target=" os-arch)
                    "--initialize-at-build-time=com.fasterxml.jackson,clj_tuple,potemkin,clj_tuple$hash_map,clj_tuple$vector"
                    "--initialize-at-run-time=org.lwjgl" 
                    "-o" graal-bin]]
    (minusthree-uber {:uber-file graal-uber
                      :basis     basis})
    (println "running" (str/join " " (into [] (map #(if (> (count %) 64) (str (subs % 0 64) "...") %))  graal-cmd)))
    (io/make-parents graal-bin)
    (b/process {:command-args graal-cmd})))

(defn run-standalone [& _]
  (let [windows?   (ms-windows?)
        os         (if windows? "windows" "linux")
        arch       (System/getProperty "os.arch")
        os-arch    (str os "-" arch)
        graal-bin  (format "%s/%s-%s-%s%s" rel-dir (name game) version os-arch (if windows? ".exe" ""))]
    (println "running" graal-bin)
    (b/process {:command-args [graal-bin]})))
