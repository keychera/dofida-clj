(ns engine.start
  (:require
   [engine.engine :as engine]
   [engine.session :as session]
   [goog.events :as events]
   [leva.core :as leva]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as pc]
   [reagent.dom.client :as rdomc]))

(defn msec->sec [n]
  (* 0.001 n))

(defn game-loop [game]
  (let [game (engine/tick game)]
    (js/requestAnimationFrame
     (fn [ts]
       (let [ts (msec->sec ts)]
         (game-loop (assoc game
                           :delta-time (- ts (:total-time game))
                           :total-time ts)))))))

(defn mousecode->keyword [mousecode]
  (condp = mousecode
    0 :left
    2 :right
    nil))

(defn listen-for-mouse [canvas]
  (events/listen js/window "mousemove"
                 (fn [event]
                   (let [bounds (.getBoundingClientRect canvas)
                         x (- (.-clientX event) (.-left bounds))
                         y (- (.-clientY event) (.-top bounds))]
                     (engine/update-mouse-coords! x y))))
  #_(events/listen js/window "mousedown"
                   (fn [event]
                     (swap! engine/*state assoc :mouse-button (mousecode->keyword (.-button event)))))
  #_(events/listen js/window "mouseup"
                   (fn [event]
                     (swap! engine/*state assoc :mouse-button nil))))

(defn keycode->keyword [keycode]
  (condp = keycode
    37 :left
    39 :right
    38 :up
    nil))

(defn listen-for-keys []
  #_(events/listen js/window "keydown"
                   (fn [event]
                     (when-let [k (keycode->keyword (.-keyCode event))]
                       (swap! engine/*state update :pressed-keys conj k))))
  #_(events/listen js/window "keyup"
                   (fn [event]
                     (when-let [k (keycode->keyword (.-keyCode event))]
                       (swap! engine/*state update :pressed-keys disj k)))))

(defn resize [context]
  (let [display-width context.canvas.clientWidth
        display-height context.canvas.clientHeight]
    (set! context.canvas.width display-width)
    (set! context.canvas.height display-height)
    (engine/update-window-size! display-width display-height)))

(defn ^:vibe listen-for-resize [context]
  (let [canvas context.canvas
        debounce-timer (atom nil)
        observer (js/ResizeObserver.
                  (fn [_entries]
                    (when @debounce-timer
                      (js/clearTimeout @debounce-timer))
                    (reset! debounce-timer
                            (js/setTimeout #(resize context) 200))))] ; 200ms after last resize event
    (.observe observer canvas)))

;; leva
(defn main-panel []
  [leva/Controls
   {:folder {:name "Control"} 
    :schema {:color {:r 30 :g 30 :b 30 
                     :onChange (fn [{:keys [r g b]}]
                                 (swap! session/*session o/insert ::session/leva-color
                                        {::session/r r ::session/g g ::session/b b}))} 
             :point {:x 0 :y 0
                     :onChange (fn [{:keys [x y]}]
                                 (swap! session/*session o/insert ::session/leva-point
                                        {::session/x x ::session/y y}))}}
    }])

(defonce root (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn ^:export ^:dev/after-load run-reagent []
  (rdomc/render @root [main-panel]))

;; start the game
(defonce context
  (let [canvas (js/document.querySelector "canvas")
        context (.getContext canvas "webgl2")
        initial-game (assoc (pc/->game context)
                            :delta-time 0
                            :total-time (msec->sec (js/performance.now)))]
    (engine/init initial-game)
    (listen-for-mouse canvas)
    (listen-for-keys)
    (resize context)
    (listen-for-resize context)
    (run-reagent)
    (game-loop initial-game)
    context))
