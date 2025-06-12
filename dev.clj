(defmulti task first)

(defmethod task :default
  [[task-name]]
  (println "Unknown task:" task-name)
  (System/exit 1))

(defmethod task nil
  [_]
  (require '[figwheel.main :as figwheel])
  ((resolve 'figwheel/-main) "--build" "dev"))

(defmethod task "native"
  [_]
  (require '[engine.start-dev])
  ((resolve 'engine.start-dev/start)))

(defmethod task "repl"
  [_]
  (clojure.main/repl :init #(doto 'engine.start-dev require in-ns)))

(task *command-line-args*)
