(ns engine.start
  (:require
   [clojure.set :refer [difference]]
   [clojure.string :as string]
   [engine.engine :as engine]
   [engine.world :as world]
   [goog.events :as events]
   [rules.interface.input :as input]))

;; reason, it seems the rate of events is faster than the gameloop rate
;; previously, queue is used and num of input events > num of frames
(def world-init-inputs
  {::mousemove    {:dx 0 :dy 0}
   ::keydown      #{}
   ::prev-keydown #{}})
(def world-inputs (atom world-init-inputs))
(def current-mode (atom ::input/blende))

(defn update-world [game]
  (let [inputs @world-inputs]
    (case (::flag inputs) 
      ::lockchange
      (let [new-mode (case @current-mode
                       ::input/blende      ::input/firstperson
                       ::input/firstperson ::input/blende)]
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
                          ::mousemove (comp (fn mouse-move [world] (input/update-mouse-delta world (:dx input-data) (:dy input-data))) prev-fn)
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
    0 ::input/mouse-left
    1 ::input/mouse-middle
    2 ::input/mouse-right
    nil))

(def mousemove-timer (atom nil))
(def locked?*        (atom nil))

(defn listen-for-pointer-lock []
  (.addEventListener js/document "pointerlockchange"
                     (fn []
                       (let [canvas-locked? (= (.. js/document -pointerLockElement)
                                               (.querySelector js/document "canvas"))]
                         (reset! locked?* canvas-locked?)
                         (swap! world-inputs assoc ::flag ::lockchange)))))

(defn listen-for-mouse [canvas]
  (.addEventListener canvas "mousemove"
                     (fn [event]
                       (if @locked?*
                         (let [next-dx (.-movementX event)
                               next-dy (.-movementY event)]
                           (some->> @mousemove-timer (.clearTimeout js/window))
                           (swap! world-inputs update ::mousemove
                                  (fn mouse-delta [prev] (-> prev (assoc :dx next-dx) (assoc :dy next-dy))))
                           (reset! mousemove-timer
                                   (.setTimeout js/window
                                                (fn mouse-stop []
                                                  (swap! world-inputs update ::mousemove (fn [prev] (-> prev (assoc :dx 0) (assoc :dy 0)))))
                                                20)))
                         (let [bounds (.getBoundingClientRect canvas)
                               x (- (.-clientX event) (.-left bounds))
                               y (- (.-clientY event) (.-top bounds))]
                           (swap! world-inputs update ::mousepos
                                  (fn mouse-delta [prev] (-> prev (assoc :x x) (assoc :y y))))))))
  (.addEventListener canvas "mousedown"
                     (fn [event]
                       (when-let [mouse (mousecode->keyword (.-button event))]
                         (swap! world-inputs update ::keydown (fn [s] (conj s mouse))))))
  (.addEventListener canvas "mouseup"
                     (fn [event]
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
                       (when (not @locked?*)
                         (.requestPointerLock canvas (clj->js {:unadjustedMovement true})))

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
         context (.getContext canvas "webgl2")
         initial-game (assoc (engine/->game context)
                             :delta-time 0
                             :total-time (js/performance.now))]
     (listen-for-mouse canvas)
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
