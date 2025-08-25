(ns engine.start-dev
  (:require
   [cider.nrepl :refer [cider-nrepl-handler]]
   [clojure.spec.test.alpha :as st]
   [clojure.tools.namespace.repl :as tn]
   [engine.refresh :as refresh]
   [engine.start :as start]
   [nrepl.server :as nrepl-server]
   [play-cljc.gl.core :as pc]))

(tn/set-refresh-dirs "src")

(defn before-refresh []
  (println "Reloading..."))

(defn after-refresh []
  (refresh/set-refresh)
  (println "Reloaded OK"))

;; Define a custom reset fn
(defn reset []
  (tn/refresh :before 'engine.start-dev/before-refresh
              :after 'engine.start-dev/after-refresh))

;; Run game non-blocking
(defonce stop-flag* (atom false))

(defn start-game []
  (st/instrument)
  (let [window (start/->window)
        game (pc/->game (:handle window))]
    (reset! stop-flag* false)
    (start/start game window stop-flag*)))

(defn stop []
  (println "stopping game...")
  (reset! stop-flag* true))

(defn restart []
  (stop)
  (reset)
  (start-game))

(defn -main []
  (println "game REPL...")
  (nrepl-server/start-server :port 53130 :handler cider-nrepl-handler)
  (start-game))

(comment
  (println "hello")

  (start-game)

  (stop)
  @stop-flag*

  (reset)

  (restart))