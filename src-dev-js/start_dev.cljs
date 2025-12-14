(ns start-dev
  (:require
   [clojure.spec.test.alpha :as st]
   [engine.start :as start]
   [leva.core :as leva]
   [reagent.core :as r]
   [reagent.dom.client :as rdomc]))

;; (st/instrument)

(defonce fps-counter*
  (r/atom {:last-time (js/performance.now)
           :frames 0
           :delta-ms 0
           :fps 0}))

(defn update-fps! [game]
  (let [{:keys [delta-time total-time]} game
        {:keys [last-time frames]} @fps-counter*
        since-last-time (- total-time last-time)]
    (if (> since-last-time 1000) ;; 1 second has passed
      (swap! fps-counter* assoc :last-time total-time :frames 0 :fps frames)
      (swap! fps-counter* assoc :frames (inc frames) :delta-ms delta-time))))

(defn main-panel []
  [:<>
   [leva/Controls
    {:folder {:name "FPS"}
     :atom   fps-counter*
     :schema {"fps graph" (leva/monitor (fn [] (:fps @fps-counter*)) {:graph true :interval 200})
              :fps        {:order 1}
              :delta-ms   {:order 2 :suffix " ms"}
              :last-time  {:render (constantly false)}}}]])

(defonce root (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn ^:export run-reagent [] (rdomc/render @root [main-panel]))

(defn dev-loop [game]
  (update-fps! game))

(defonce dev-only
  (do (run-reagent)
      (start/-main {::start/callback-fn dev-loop})))

(comment
  
  (st/instrument)
  
  (st/unstrument)
  
  :-)