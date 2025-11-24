(ns start-dev
  (:require
   [cider.nrepl :refer [cider-nrepl-handler]]
   [clojure.spec.test.alpha :as st]
   [engine.engine :as engine]
   [engine.refresh :as refresh]
   [engine.start :as start]
   #_[gui.debug-ui :as debug-ui]
   [nrepl.server :as nrepl-server])
  ;; imgui is disabled for now bc it err on wsl
  #_(:import
     (imgui ImGui)
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
  (with-redefs
   [start/is-mouse-blocked? (fn [] false #_(.getWantCaptureMouse (ImGui/getIO)))]
    (let [window (start/->window true)
          game   (engine/->game (:handle window))
          #_#_imguiGlfw (ImGuiImplGlfw.)
          #_#_imGuiGl3 (ImGuiImplGl3.)
          #_#_callback #::start{:init-fn (partial debug-ui/init imguiGlfw imGuiGl3)
                                :frame-fn (partial debug-ui/frame imguiGlfw imGuiGl3)
                                :destroy-fn (partial debug-ui/destroy imguiGlfw imGuiGl3)
                                :stop-flag* stop-flag*}]
      (start/start game window {}))))

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