(ns start-dev
  (:require
   [cider.nrepl :refer [cider-nrepl-handler]]
   [clojure.spec.test.alpha :as st]
   [engine.engine :as engine]
   [engine.refresh :as refresh]
   [engine.start :as start]
   [gui.debug-ui :as debug-ui]
   [nrepl.server :as nrepl-server])
  (:import
   (imgui.gl3 ImGuiImplGl3)
   (imgui.glfw ImGuiImplGlfw)))

(defn refresh []
  (refresh/set-refresh))

(defonce stop-flag* (atom false))

(defn stop []
  (println "stopping game...")
  (reset! stop-flag* true))

(defn start []
  (st/instrument)
  (reset! stop-flag* false)
  (let [window (start/->window true)
        game   (engine/->game (:handle window))
        imguiGlfw (ImGuiImplGlfw.) imGuiGl3 (ImGuiImplGl3.)
        callback #::start{:init-fn (partial debug-ui/init imguiGlfw imGuiGl3)
                          :frame-fn (partial debug-ui/frame imguiGlfw imGuiGl3)
                          :destroy-fn (partial debug-ui/destroy imguiGlfw imGuiGl3)
                          :stop-flag* stop-flag*}]
    (start/start game window callback)))

(defn -main []
  (let [nrepl-server (nrepl-server/start-server :handler cider-nrepl-handler)]
    (println "game REPL...")
    (spit ".nrepl-port" (:port nrepl-server)))
  (while true (start)) ;; forever start so after (stop), it will (start) again
  (shutdown-agents))

(comment
  (refresh)
  (stop) ;; game won't load properly on the second start

  ::waiting-for-something-to-happen?)