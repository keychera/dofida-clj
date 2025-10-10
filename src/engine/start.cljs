(ns engine.start
  (:require
   [engine.engine :as engine]
   [goog.events :as events]))

(defn game-loop
  ([game] (game-loop game nil))
  ([game {::keys [callback-fn] :as config}]
   (let [game (engine/tick game)]
     (js/requestAnimationFrame
      (fn [ts]
        (let [ts ts]
          (when callback-fn (callback-fn game))
          (game-loop (assoc game
                            :delta-time (- ts (:total-time game))
                            :total-time ts)
                     config)))))))

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
                         y (- (.-clientY event) (.-top bounds))])))
  (events/listen js/window "mousedown"
                 (fn [event]))
  (events/listen js/window "mouseup"
                 (fn [event])))

(defn keycode->keyword [keycode]
  (condp = keycode
    37 :left
    39 :right
    38 :up
    nil))

(defn listen-for-keys []
  (events/listen js/window "keydown"
                 (fn [event]
                   (when-let [k (keycode->keyword (.-keyCode event))])))
  (events/listen js/window "keyup"
                 (fn [event]
                   (when-let [k (keycode->keyword (.-keyCode event))]))))

(defn resize [context]
  (let [display-width context.canvas.clientWidth
        display-height context.canvas.clientHeight]
    (set! context.canvas.width display-width)
    (set! context.canvas.height display-height)))

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

;; start the game
(defn -main
  ([] (-main nil))
  ([config]
   (let [canvas (js/document.querySelector "canvas")
         context (.getContext canvas "webgl2" (clj->js {:alpha false}))
         initial-game (assoc (engine/->game context)
                             :delta-time 0
                             :total-time (js/performance.now))]
     (engine/init initial-game)
     (listen-for-mouse canvas)
     (listen-for-keys)
     (resize context)
     (listen-for-resize context)
     (game-loop initial-game config)
     context)))
