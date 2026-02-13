(ns minusthree.-dev.start
  (:require
   [cider.nrepl :refer [cider-nrepl-handler]]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [com.phronemophobic.viscous :as viscous]
   [minusthree.platform.jvm.jvm-game :as jvm-game]
   [nrepl.server :as nrepl-server]))

(defn get-config []
  (let [default {:window-conf {:w 1024 :h 768 :x 500 :y 500}}
        config  "config.edn"]
    (try (with-open [rdr (io/reader (io/input-stream config))]
           (edn/read (java.io.PushbackReader. rdr)))
         (catch java.io.FileNotFoundException _
           (spit config default)))))

(defonce stop* (atom false))
(defonce refresh* (atom false))

(defn toggle-stop []
  (if (swap! stop* not)
    "stopping game..."
    "starting game..."))

(defn refresh []
  (reset! refresh* true)
  "refreshing game...")

(defn start []
  (let [dev-config (assoc (get-config)
                          :stop-flag* stop*
                          :refresh-flag* refresh*)
        window     (jvm-game/create-window (:window-conf dev-config))]
    (jvm-game/start window dev-config)))

(defn -main [& _]
  (st/instrument 'odoyle.rules/insert)
  (s/check-asserts true)
  (let [nrepl-server (nrepl-server/start-server :handler cider-nrepl-handler)]
    (println "game REPL...")
    (spit ".nrepl-port" (:port nrepl-server)))
  (while true
    (try
      (when (not @stop*)
        (start)
        (reset! stop* true))
      (catch Throwable e
        (reset! stop* true)
        (viscous/inspect (update (Throwable->map e) :cause
                                 (fn [txt] (some-> txt (str/split-lines)))))))))

(comment
  (toggle-stop)
  (refresh)

  (st/instrument)
  (st/unstrument)

  ::waiting-for-something-to-happen?)
