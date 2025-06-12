(ns ^:figwheel-hooks engine.refresh)

(def *refresh? (atom false))

(defn ^:after-load set-refresh []
  (swap! *refresh? (constantly true)))
