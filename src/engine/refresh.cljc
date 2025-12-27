(ns engine.refresh)

(defonce *refresh? (atom false))

(defn ^:dev/after-load set-refresh []
  (swap! *refresh? (constantly true)))
