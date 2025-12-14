(ns platform.start
  (:require
   [clojure.set :refer [difference]]
   [clojure.string :as string]
   [engine.engine :as engine]
   [engine.game :as game]
   [engine.world :as world]
   [goog.events :as events]
   [minustwo.systems.input :as input]))

;; reason, it seems the rate of events is faster than the gameloop rate
;; previously, queue is used and num of input events > num of frames
(def world-init-inputs
  {::pointer-move    {:dx 0 :dy 0}
   ::pointer-pos     {:x 0  :y 0}
   ::keydown      #{}
   ::prev-keydown #{}})
(def world-inputs (atom world-init-inputs))
(def current-mode (atom ::input/arcball))

(defn update-world [game]
  (let [inputs @world-inputs]
    (case (::flag inputs)
      ::lockchange
      (let [new-mode (case @current-mode
                       ::input/arcball     ::input/firstperson
                       ::input/firstperson ::input/arcball)]
        (reset! current-mode new-mode)
        (swap! (::world/atom* game)
               (fn [world']
                 (-> world'
                     (input/update-mouse-delta 0 0)
                     (input/cleanup-input)
                     (input/set-mode new-mode))))
        (reset! world-inputs world-init-inputs))

      (let [input-fn (reduce
                      (fn [prev-fn [input-key input-data]]
                        (case input-key
                          ::pointer-pos  (comp (fn pointer-pos [world] (input/update-mouse-pos world (:x input-data) (:y input-data))) prev-fn)
                          ::pointer-move (comp (fn pointer-move [world] (input/update-mouse-delta world (:dx input-data) (:dy input-data))) prev-fn)
                          ::keydown   (loop [[k & remains] input-data acc-fn prev-fn]
                                        (if k
                                          (recur remains (comp (fn keydown [world] (input/key-on-keydown world k)) acc-fn))
                                          acc-fn))
                          prev-fn))
                      identity inputs)
            keyups   (difference (::prev-keydown inputs) (::keydown inputs))
            _        (swap! world-inputs assoc ::prev-keydown (::keydown inputs))
            input-fn (loop [[k & remains] keyups acc-fn input-fn]
                       (if k
                         (recur remains (comp (fn keyup [world] (input/key-on-keyup world k)) acc-fn))
                         acc-fn))]
        (swap! (::world/atom* game) input-fn)))))

(defn game-loop
  ([game] (game-loop game nil))
  ([game {::keys [callback-fn error-fn]
          :or {error-fn #(throw %)}
          :as config}]
   (let [game (try (engine/tick game)
                   (catch js/Error e (error-fn e)))]
     (js/requestAnimationFrame
      (fn [ts]
        (when callback-fn (callback-fn game))
        (game-loop (assoc game
                          :delta-time (- ts (:total-time game))
                          :total-time ts)
                   config))))))

(defn mousecode->keyword [mousecode]
  (condp = mousecode
    0 ::input/mouse-left
    1 ::input/mouse-middle
    2 ::input/mouse-right
    nil))

(def pointermove-timer (atom nil))
(def locked?*        (atom false))

(defn listen-for-pointer-lock []
  (.addEventListener js/document "pointerlockchange"
                     (fn []
                       (let [canvas-locked? (= (.. js/document -pointerLockElement)
                                               (.querySelector js/document "canvas"))]
                         (if (false? canvas-locked?) ;; using false? to show intent, wait for 1.2s to sync locked?* to false
                           ;; https://discourse.threejs.org/t/how-to-avoid-pointerlockcontrols-error/33017/4
                           (.setTimeout js/window (fn reset-locked-back-to-true [] (reset! locked?* false)) 1200)
                           (reset! locked?* canvas-locked?))

                         (swap! world-inputs assoc ::flag ::lockchange)))))

(defn listen-for-pointer [canvas]
  (.addEventListener canvas "pointermove"
                     (fn [event]
                       (if @locked?*
                         (let [next-dx (.-movementX event)
                               next-dy (.-movementY event)]
                           (some->> @pointermove-timer (.clearTimeout js/window))
                           (swap! world-inputs update ::pointer-move
                                  (fn pointer-delta [prev] (-> prev (assoc :dx next-dx) (assoc :dy next-dy))))
                           (reset! pointermove-timer
                                   (.setTimeout js/window
                                                (fn mouse-stop []
                                                  (swap! world-inputs update ::pointer-move (fn [prev] (-> prev (assoc :dx 0) (assoc :dy 0)))))
                                                20)))
                         (let [bounds (.getBoundingClientRect canvas)
                               x (- (.-clientX event) (.-left bounds))
                               y (- (.-clientY event) (.-top bounds))]
                           (swap! world-inputs update ::pointer-pos
                                  (fn pointer-pos [prev] (-> prev (assoc :x x) (assoc :y y))))))))
  (.addEventListener canvas "pointerdown"
                     (fn [event]
                       (when (not @locked?*) (.setPointerCapture canvas (.-pointerId event)))
                       (when-let [mouse (mousecode->keyword (.-button event))]
                         (swap! world-inputs update ::keydown (fn [s] (conj s mouse))))))
  (.addEventListener canvas "pointerup"
                     (fn [event]
                       (when (not @locked?*) (.releasePointerCapture canvas (.-pointerId event)))
                       (when-let [mouse (mousecode->keyword (.-button event))]
                         (swap! world-inputs update ::keydown (fn [s] (disj s mouse)))))))

(defn keycode->keyword [keycode]
  (cond
    (= keycode 32) :space
    (= keycode 37) :left
    (= keycode 38) :up
    (= keycode 39) :right
    (= keycode 40) :down

    (= keycode 16) :shift
    (= keycode 17) :ctrl
    (= keycode 18) :alt
    (= keycode 91) :meta   ; Command on macOS / Windows key on Windows
    (= keycode 93) :meta   ; Right Command / Menu key

    ;; numbers 0–9
    (<= 48 keycode 57)
    (keyword (str "num" (char keycode)))

    ;; letters A–Z
    (<= 65 keycode 90)
    (keyword (-> keycode char str string/lower-case))

    :else nil))

(defn listen-for-keys [canvas]
  (events/listen js/window "keydown"
                 (fn [event]
                   (when-let [keyname (keycode->keyword (.-keyCode event))]
                     (case keyname
                       :num1 ;; we stop all 1 to world-inputs for now 
                       (let [lock-status @locked?*]
                         (swap! locked?* :pending)
                         (case lock-status
                           true  (.exitPointerLock js/document)
                           false (.requestPointerLock canvas (clj->js {:unadjustedMovement true}))
                           :noop))

                       (swap! world-inputs update ::keydown (fn [s] (conj s keyname)))))))
  (events/listen js/window "keyup"
                 (fn [event]
                   (when-let [keyname (keycode->keyword (.-keyCode event))]
                     (swap! world-inputs update ::keydown (fn [s] (disj s keyname)))))))

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
         context (.getContext canvas "webgl2" (clj->js {:premultipliedAlpha false}))
         initial-game (game/->game {:webgl-context context
                                    :delta-time 0
                                    :total-time (js/performance.now)})]
     (listen-for-pointer canvas)
     (listen-for-pointer-lock)
     (engine/init initial-game)
     (listen-for-keys canvas)
     (resize context)
     (listen-for-resize context)
     (game-loop initial-game
                (update config
                        ::callback-fn
                        (fn callback-fn [afn]
                          (fn [game]
                            (when afn (afn game))
                            (update-world game)))))
     context)))
