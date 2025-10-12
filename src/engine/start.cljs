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

(defn update-world [game]
  (let [inputs   @world-inputs
        input-fn (reduce
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
    (swap! (::world/atom* game) input-fn)))

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

(def mousemove-timer (atom nil))
(def locked?*        (atom nil))

(defn listen-for-mouse [canvas]
  (.addEventListener canvas "mousemove"
                     (fn [event]
                       (when @locked?*
                         (some->> @mousemove-timer (.clearTimeout js/window))
                         (let [next-dx (.-movementX event)
                               next-dy (.-movementY event)]
                           (swap! world-inputs update ::mousemove
                                  (fn mouse-delta [prev] (-> prev (assoc :dx next-dx) (assoc :dy next-dy))))
                           (reset! mousemove-timer
                                   (.setTimeout js/window
                                                (fn mouse-stop []
                                                  (swap! world-inputs update ::mousemove (fn [prev] (-> prev (assoc :dx 0) (assoc :dy 0)))))
                                                20))))))
  (.addEventListener canvas "mousedown"
                     (fn [_event]))
  (.addEventListener canvas "mouseup"
                     (fn [_event])))

(defn listen-for-pointer-lock [canvas]
  (.addEventListener js/document "pointerlockchange"
                     (fn []
                       (let [canvas-locked? (= (.. js/document -pointerLockElement)
                                               (.querySelector js/document "canvas"))]
                         (reset! locked?* canvas-locked?)
                         (when (not canvas-locked?)
                           (reset! world-inputs world-init-inputs)))))
  (.addEventListener canvas "click"
                     (fn [_event]
                       (when (nil? @locked?*)
                         (listen-for-mouse canvas))
                       (.requestPointerLock canvas (clj->js {:unadjustedMovement true})))))

(defn keycode->keyword [keycode]
  (cond
    ;; arrow keys
    (= keycode 37) :left
    (= keycode 38) :up
    (= keycode 39) :right
    (= keycode 40) :down

    ;; numbers 0–9
    (<= 48 keycode 57)
    (keyword (str (char keycode)))

    ;; letters A–Z
    (<= 65 keycode 90)
    (keyword (-> keycode char str string/lower-case))

    :else nil))


(defn listen-for-keys []
  (events/listen js/window "keydown"
                 (fn [event]
                   (when-let [keyname (keycode->keyword (.-keyCode event))]
                     (swap! world-inputs update ::keydown (fn [s] (conj s keyname))))))
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
         context (.getContext canvas "webgl2" (clj->js {:alpha false}))
         initial-game (assoc (engine/->game context)
                             :delta-time 0
                             :total-time (js/performance.now))]
     (listen-for-pointer-lock canvas)
     (engine/init initial-game)
     (listen-for-keys)
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
