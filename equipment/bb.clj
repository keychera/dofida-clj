(ns bb
  (:require
   [clojure.tools.build.api :as b]))

(defn c [& _]
  (b/process {:dir "src-c" :command-args ["gcc" "hello.c"]}))

(defn runc [& _]
  (b/process {:command-args ["src-c/a.exe"] :out :inherit}))
