(ns engine.start-dev
  (:require
   [clojure.data :as data]
   [clojure.set :as set]
   [clojure.spec.test.alpha :as st]
   [engine.start :as start]
   [engine.world :as world]
   [leva.core :as leva]
   [odoyle.rules :as o]
   [reagent.core :as r]
   [reagent.dom.client :as rdomc]
   [shadow.cljs.devtools.client.hud :as shadow-hud]
   [shadow.dom :as shadow-dom]
   [systems.dev.dev-only :as dev-only]
   [systems.dev.leva-rules :as leva-rules]))

(st/instrument)

(defonce fps-counter*
  (r/atom {:last-time (js/performance.now) :frames 0 :fps 0}))

(defn ^:vibe update-fps! []
  (let [now (js/performance.now)
        {:keys [last-time frames]} @fps-counter*
        delta (- now last-time)]
    (if (> delta 1000) ;; 1 second has passed
      (swap! fps-counter* assoc :last-time now :frames 0 :fps frames)
      (swap! fps-counter* update :frames inc))))

(defonce leva-atom*
  (r/atom {:color {:r 200 :g 120 :b 120}
           :point {:x 0 :y 0}}))

(defmulti on-leva-change (fn [k _old _new] k))


(defmethod on-leva-change :color [_ _ {:keys [r g b]}]
  (swap! world/world* o/insert ::leva-rules/leva-color {::leva-rules/r r ::leva-rules/g g ::leva-rules/b b}))

(defmethod on-leva-change :point [_ _ {:keys [x y]}]
  (swap! world/world* o/insert ::leva-rules/leva-point {::leva-rules/x x ::leva-rules/y y}))

(defmethod on-leva-change :default [_k _old' _new']
  #_(println k old' new'))

(defn leva-watcher [_ _ old' new']
  (let [[removed added _common] (data/diff old' new')]
    (doseq [k (set/union (set (keys removed)) (set (keys added)))]
      (on-leva-change k (get old' k) (get new' k)))))


(add-watch leva-atom* :leva-watcher #'leva-watcher)


(defonce dev-atom*
  (r/atom {:dev-value "raw value"}))

(def !hud-visible (atom false))

(defn listen-to-dev-events! []
  (let [warning (first (o/query-all @world/world* ::dev-only/warning))]
    (if (some? warning)
      (when (not @!hud-visible)
        (shadow-hud/hud-warnings {:info {:sources [{:warnings [{:resource-name "code"
                                                                :msg (:message warning)
                                                                :source-excerpt "what to pass here?"}]}]}})
        (reset! !hud-visible true))
      (when @!hud-visible
        (shadow-dom/remove (shadow-dom/by-id shadow-hud/hud-id))
        (reset! !hud-visible false)))
    (if-let [dev-value (first (o/query-all @world/world* ::dev-only/dev-value))]
      (swap! dev-atom* assoc :dev-value (pr-str (:value dev-value)))
      (swap! dev-atom* assoc :dev-value "raw value"))))

(defn main-panel []
  [:<>
   [leva/Controls
    {:folder {:name "FPS"}
     :atom   fps-counter*
     :schema {"fps graph" (leva/monitor (fn [] (:fps @fps-counter*)) {:graph true :interval 200})
              :fps        {:order 1}
              :last-time  {:render (constantly false)}}}]
   [leva/Controls
    {:folder {:name "Control"}
     :atom   leva-atom*}]
   [leva/Controls
    {:folder {:name "Dev"}
     :atom   dev-atom*
     :schema {:dev-value {:order 0}}}]])

(defonce root (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn ^:export run-reagent [] (rdomc/render @root [main-panel]))

(defn dev-loop []
  (update-fps!)
  (listen-to-dev-events!))

(defonce dev-only
  (do (run-reagent)
      (start/-main dev-loop)))