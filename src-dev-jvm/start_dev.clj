(ns start-dev
  (:require
   [cider.nrepl :refer [cider-nrepl-handler]]
   [clojure.spec.test.alpha :as st]
   [engine.game :as game]
   [engine.refresh :as refresh]
   [gui.debug-ui :as debug-ui]
   [nrepl.server :as nrepl-server]
   [platform.start :as start]
   [com.phronemophobic.viscous :as viscous]
   [clojure.spec.alpha :as s]
   [clojure.string :as str])
  (:import
   (imgui ImGui)
   (imgui.gl3 ImGuiImplGl3)
   (imgui.glfw ImGuiImplGlfw)))

(defn refresh []
  (refresh/set-refresh))

(defonce stop* (atom false))

(defn toggle-stop []
  (if (swap! stop* not)
    (println "stopping game...")
    (println "starting game...")))

(defn start []
  (st/instrument 'odoyle.rules/insert)
  (s/check-asserts true)
  (reset! stop* false)
  (with-redefs
   [start/is-mouse-blocked? (fn [] (.getWantCaptureMouse (ImGui/getIO)))]
    (let [window (start/->window true)
          game   (game/->game {:glfw-window window})
          imguiGlfw (ImGuiImplGlfw.)
          imGuiGl3 (ImGuiImplGl3.)
          callback #::start{:init-fn (partial debug-ui/init imguiGlfw imGuiGl3)
                            :frame-fn (partial debug-ui/frame imguiGlfw imGuiGl3)
                            :destroy-fn (partial debug-ui/destroy imguiGlfw imGuiGl3)
                            :stop-flag* stop*}]
      (start/start game window callback))))

(defn -main []
  (let [nrepl-server (nrepl-server/start-server :handler cider-nrepl-handler)]
    (println "game REPL...")
    (spit ".nrepl-port" (:port nrepl-server)))
  (while true
    (try
      (when (not @stop*)
        (start))
      (catch Throwable e
        (reset! stop* true)
        (viscous/inspect (update (Throwable->map e) :cause
                                 (fn [txt] (some-> txt (str/split-lines))))))))
  (shutdown-agents))

(comment
  (refresh)
  (toggle-stop)

  (st/instrument)
  (st/unstrument)

  ::waiting-for-something-to-happen?)