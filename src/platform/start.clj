(ns platform.start
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.set :refer [difference]]
   [engine.engine :as engine]
   [engine.game :as game]
   [engine.world :as world]
   [minustwo.systems.input :as input])
  (:import
   [org.lwjgl.glfw
    Callbacks
    GLFW
    GLFWCharCallbackI
    GLFWCursorPosCallbackI
    GLFWFramebufferSizeCallbackI
    GLFWKeyCallbackI
    GLFWMouseButtonCallbackI
    GLFWScrollCallbackI
    GLFWWindowCloseCallbackI]
   [org.lwjgl.opengl GL GL33]
   [org.lwjgl.system MemoryUtil])
  (:gen-class))

(def world-init-inputs
  {::pointer-move {:dx 0.0 :dy 0.0}
   ::pointer-pos  {:x 0.0  :y 0.0}
   ::keydown      #{}
   ::prev-keydown #{}})

(defonce world-inputs (atom world-init-inputs))

(defonce mouse-state (atom {::last-mousemove (System/nanoTime) ::stopped? true}))
(defonce mousestop-threshold-nanos 50000000) ;; 50ms
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
                     (input/update-mouse-delta 0.0 0.0)
                     (input/cleanup-input)
                     (input/set-mode new-mode))))
        (reset! world-inputs world-init-inputs))

      (let [input-fn (reduce
                      (fn [prev-fn [input-key input-data]]
                        (case input-key
                          ::pointer-pos  (comp (fn pointer-pos [world] (input/update-mouse-pos world (:x input-data) (:y input-data))) prev-fn)
                          ::pointer-move (comp (fn pointer-move [world]
                                                 (input/update-mouse-delta world (:dx input-data) (:dy input-data))) prev-fn)
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

(defn mousecode->keyword [mousecode]
  (condp = mousecode
    GLFW/GLFW_MOUSE_BUTTON_LEFT :left
    GLFW/GLFW_MOUSE_BUTTON_RIGHT :right
    nil))

(defonce mouse-locked?* (atom false))
(defn on-lock-change [_lock-state]
  (swap! world-inputs assoc ::flag ::lockchange))

(add-watch mouse-locked?* :watcher
           (fn [_ _ old-state new-state]
             (when (not= old-state new-state)
               (on-lock-change new-state))))

(defn on-mouse-stop! []
  (swap! world-inputs update ::pointer-move (fn [prev] (-> prev (assoc :dx 0.0) (assoc :dy 0.0)))))

(defn on-mouse-move! [window xpos ypos]
  (swap! mouse-state assoc ::last-mousemove (System/nanoTime) ::stopped? false)
  (let [*fb-width (MemoryUtil/memAllocInt 1)
        *fb-height (MemoryUtil/memAllocInt 1)
        *window-width (MemoryUtil/memAllocInt 1)
        *window-height (MemoryUtil/memAllocInt 1)
        _ (GLFW/glfwGetFramebufferSize window *fb-width *fb-height)
        _ (GLFW/glfwGetWindowSize window *window-width *window-height)
        fb-width (.get *fb-width)
        fb-height (.get *fb-height)
        window-width (.get *window-width)
        window-height (.get *window-height)
        width-ratio (/ fb-width window-width)
        height-ratio (/ fb-height window-height)
        x (* xpos width-ratio)
        y (* ypos height-ratio)]
    (MemoryUtil/memFree *fb-width)
    (MemoryUtil/memFree *fb-height)
    (MemoryUtil/memFree *window-width)
    (MemoryUtil/memFree *window-height)
    (if @mouse-locked?*
      (let [half-w (/ window-width 2)
            half-h (/ window-height 2)
            dx (- x half-w)
            dy (- y half-h)]
        (swap! world-inputs update ::pointer-move
               (fn pointer-delta [prev] (-> prev (assoc :dx dx) (assoc :dy dy))))
        (GLFW/glfwSetCursorPos window half-w half-h))
      (swap! world-inputs update ::pointer-pos
             (fn pointer-pos [prev] (-> prev (assoc :x x) (assoc :y y)))))))

;; to be injected from debug for now using with-redefs
(defn is-mouse-blocked? [] false)

(defn mousebutton->keyword [button]
  (condp = button
    GLFW/GLFW_MOUSE_BUTTON_LEFT   ::input/mouse-left
    GLFW/GLFW_MOUSE_BUTTON_MIDDLE ::input/mouse-middle
    GLFW/GLFW_MOUSE_BUTTON_RIGHT  ::input/mouse-right
    nil))

(defn on-mouse-click! [_window button action _mods]
  (when-let [mouse (mousebutton->keyword button)]
    (condp = action
      GLFW/GLFW_PRESS   (swap! world-inputs update ::keydown (fn [s] (conj s mouse)))
      GLFW/GLFW_RELEASE (swap! world-inputs update ::keydown (fn [s] (disj s mouse)))
      nil)))

(defn keycode->keyword [keycode]
  (condp = keycode
    GLFW/GLFW_KEY_LEFT         :left
    GLFW/GLFW_KEY_RIGHT        :right
    GLFW/GLFW_KEY_UP           :up
    GLFW/GLFW_KEY_DOWN         :down
    GLFW/GLFW_KEY_ESCAPE       :esc
    GLFW/GLFW_KEY_W            :w
    GLFW/GLFW_KEY_A            :a
    GLFW/GLFW_KEY_S            :s
    GLFW/GLFW_KEY_D            :d
    GLFW/GLFW_KEY_R            :r
    GLFW/GLFW_KEY_1            :num1
    GLFW/GLFW_KEY_LEFT_SHIFT   :shift
    GLFW/GLFW_KEY_LEFT_CONTROL :ctrl
    nil))

(defn on-key! [window keycode _scancode action _mods]
  (when-let [keyname (keycode->keyword keycode)]
    (condp = action
      GLFW/GLFW_PRESS   (cond
                          (= :esc keyname)
                          (do (swap! world-inputs update ::keydown (fn [s] (conj s keyname)))
                              (GLFW/glfwSetInputMode window GLFW/GLFW_CURSOR GLFW/GLFW_CURSOR_NORMAL)
                              (reset! mouse-locked?* false))

                          (= :num1 keyname)
                          (do (when-not @mouse-locked?*
                                (let [*window-width (MemoryUtil/memAllocInt 1)
                                      *window-height (MemoryUtil/memAllocInt 1)
                                      _ (GLFW/glfwGetWindowSize window *window-width *window-height)
                                      window-width (.get *window-width)
                                      window-height (.get *window-height)
                                      half-w (/ window-width 2)
                                      half-h (/ window-height 2)]
                                  (MemoryUtil/memFree *window-width)
                                  (MemoryUtil/memFree *window-height)
                                  (GLFW/glfwSetCursorPos window half-w half-h)
                                  ;; GLFW_CURSOR_HIDDEN doesn't work somehow
                                  (GLFW/glfwSetInputMode window GLFW/GLFW_CURSOR GLFW/GLFW_CURSOR_DISABLED)))
                              (reset! mouse-locked?* true))

                          (#{:w :a :s :d :r :shift :ctrl} keyname)
                          (swap! world-inputs update ::keydown (fn [s] (conj s keyname)))

                          :else :noop)
      GLFW/GLFW_RELEASE (cond
                          (#{:w :a :s :d :r :shift :ctrl} keyname)
                          (swap! world-inputs update ::keydown (fn [s] (disj s keyname)))

                          :else :noop)
      nil)))

(defn on-char! [_window _codepoint])

(defn on-resize! [_window _width _height])

(defn on-scroll! [_window _xoffset _yoffset])

(defprotocol Events
  (on-mouse-move [this xpos ypos])
  (on-mouse-click [this button action mods])
  (on-key [this keycode scancode action mods])
  (on-char [this codepoint])
  (on-resize [this width height])
  (on-scroll [this xoffset yoffset])
  (on-tick [this game]))

(defrecord Window [handle])

(extend-type Window
  Events
  (on-mouse-move [{:keys [handle]} xpos ypos]
    (on-mouse-move! handle xpos ypos))
  (on-mouse-click [{:keys [handle]} button action mods]
    (on-mouse-click! handle button action mods))
  (on-key [{:keys [handle]} keycode scancode action mods]
    (on-key! handle keycode scancode action mods))
  (on-char [{:keys [handle]} codepoint]
    (on-char! handle codepoint))
  (on-resize [{:keys [handle]} width height]
    (on-resize! handle width height))
  (on-scroll [{:keys [handle]} xoffset yoffset]
    (on-scroll! handle xoffset yoffset))
  (on-tick [_this game]
    (update-world game)
    (engine/tick game)))

(defn listen-for-events [{:keys [handle] :as window}]
  (doto handle
    (GLFW/glfwSetCursorPosCallback
     (reify GLFWCursorPosCallbackI
       (invoke [_this _ xpos ypos]
         (on-mouse-move window xpos ypos))))
    (GLFW/glfwSetMouseButtonCallback
     (reify GLFWMouseButtonCallbackI
       (invoke [_this _ button action mods]
         (on-mouse-click window button action mods))))
    (GLFW/glfwSetKeyCallback
     (reify GLFWKeyCallbackI
       (invoke [_this _ keycode scancode action mods]
         (on-key window keycode scancode action mods))))
    (GLFW/glfwSetCharCallback
     (reify GLFWCharCallbackI
       (invoke [_this _ codepoint]
         (on-char window codepoint))))
    (GLFW/glfwSetFramebufferSizeCallback
     (reify GLFWFramebufferSizeCallbackI
       (invoke [_this _ width height]
         (on-resize window width height))))
    (GLFW/glfwSetScrollCallback
     (reify GLFWScrollCallbackI
       (invoke [_this _ xoffset yoffset]
         (on-scroll window xoffset yoffset))))
    (GLFW/glfwSetWindowCloseCallback
     (reify GLFWWindowCloseCallbackI
       (invoke [_this _window]
         (System/exit 0))))))

;; dev only for now
(defn get-config []
  (let [default {:window [1024 768 500 500]}
        config "config.edn"]
    (try (with-open [rdr (io/reader (io/input-stream config))]
           (edn/read (java.io.PushbackReader. rdr)))
         (catch java.io.FileNotFoundException _
           (spit config default)))))

(defn ->window
  ([] (->window false))
  ([floating?]
   (when-not (GLFW/glfwInit)
     (throw (Exception. "Unable to initialize GLFW")))
   (let [config    (get-config)
         [w h x y] (:window config)]
     (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
     (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)
     (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 3)
     (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 3)
     (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_FORWARD_COMPAT GL33/GL_TRUE)
     (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_CORE_PROFILE)
     (if-let [window (GLFW/glfwCreateWindow w h "Hello, dofida!" 0 0)]
       (do
         (GLFW/glfwSetWindowPos window x, y)
         (GLFW/glfwMakeContextCurrent window)
         (GLFW/glfwSwapInterval 1)
         (GL/createCapabilities)
         (when floating? (GLFW/glfwSetWindowAttrib window GLFW/GLFW_FLOATING GLFW/GLFW_TRUE))
         (->Window window))
       (throw (Exception. "Failed to create window"))))))



(defn start
  ([game window] (start game window nil))
  ([game window {::keys [init-fn frame-fn destroy-fn stop-flag*]}]
   (let [handle (:handle window)
         game (assoc game :delta-time 0.0 :total-time (* (GLFW/glfwGetTime) 1000))]
     (GLFW/glfwShowWindow handle)
     (engine/init game)
     (listen-for-events window)
     (when init-fn (init-fn window))
     (try
       (loop [game game]
         (when-not (or (GLFW/glfwWindowShouldClose handle)
                       (and (some? stop-flag*) @stop-flag*))
           (let [ts (* (GLFW/glfwGetTime) 1000)
                 game (assoc game
                             :delta-time (- ts (:total-time game))
                             :total-time ts)
                 game (on-tick window game)]
             (when frame-fn (frame-fn))
             (GLFW/glfwSwapBuffers handle)
             (GLFW/glfwPollEvents)
             (let [{::keys [stopped? last-mousemove]} @mouse-state]
               (when (and (not stopped?) (> (- (System/nanoTime) last-mousemove) mousestop-threshold-nanos))
                 (swap! mouse-state assoc ::stopped? true)
                 (on-mouse-stop!)))
             (recur game))))
       (finally
         (when destroy-fn (destroy-fn))
         (Callbacks/glfwFreeCallbacks handle)
         (GLFW/glfwDestroyWindow handle)
         (GLFW/glfwTerminate))))))

(defn -main [& _args]
  (let [window (->window)]
    (start (game/->game {:glfw-window window}) window)))

