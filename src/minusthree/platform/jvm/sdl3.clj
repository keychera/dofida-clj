(ns minusthree.platform.jvm.sdl3
  (:import
   [org.lwjgl.sdl
    SDLError
    SDLEvents
    SDLInit
    SDLTimer
    SDLVideo
    SDL_Event]))

;; learning from 
;; https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/sdl/Gears.java
;; https://github.com/ocornut/imgui/blob/master/examples/example_sdl3_opengl3/main.cpp

(defn throw-sdl-error [msg]
  (throw (ex-info msg {:data (SDLError/SDL_GetError)})))

(defn create-window
  ([{:keys [w h title]
     :or   {w 1280 h 720 title "Hello, dofida-3 + sdl3!"}}]
   ;; gl ver
   (SDLVideo/SDL_GL_SetAttribute SDLVideo/SDL_GL_CONTEXT_FLAGS SDLVideo/SDL_GL_CONTEXT_FORWARD_COMPATIBLE_FLAG)
   (SDLVideo/SDL_GL_SetAttribute SDLVideo/SDL_GL_CONTEXT_PROFILE_MASK SDLVideo/SDL_GL_CONTEXT_PROFILE_CORE)
   (SDLVideo/SDL_GL_SetAttribute SDLVideo/SDL_GL_CONTEXT_MAJOR_VERSION 4)
   (SDLVideo/SDL_GL_SetAttribute SDLVideo/SDL_GL_CONTEXT_MINOR_VERSION 2)

   ;; graphics context
   (SDLVideo/SDL_GL_SetAttribute SDLVideo/SDL_GL_DOUBLEBUFFER 1)
   (SDLVideo/SDL_GL_SetAttribute SDLVideo/SDL_GL_DEPTH_SIZE 24)
   (SDLVideo/SDL_GL_SetAttribute SDLVideo/SDL_GL_STENCIL_SIZE 0)
   (let [window-flags (bit-or SDLVideo/SDL_WINDOW_OPENGL SDLVideo/SDL_WINDOW_RESIZABLE)
         width        (int w)
         height       (int h)
         sdl-window   (SDLVideo/SDL_CreateWindow title width height window-flags)]
     (when (= sdl-window 0) (throw-sdl-error "SDLVideo/SDL_CreateWindow error!"))
     sdl-window)))

(defn create-gl-context [sdl-window]
  (let [gl-context (SDLVideo/SDL_GL_CreateContext sdl-window)]
    (when (= gl-context 0) (throw-sdl-error "SDLVideo/SDL_GL_CreateContext error!"))
    gl-context))

(defn start-sdl-loop
  ([window {:keys [stop-flag* window-conf]}]
   (println "hello -3 + sdl3")
   (println "setting up:" window-conf)
   (when-not (SDLInit/SDL_Init SDLInit/SDL_INIT_VIDEO)
     (throw-sdl-error "SDLInit/SDL_Init error!"))
   (let [gl-context (create-gl-context window)
         running?*  (atom true)
         event      (SDL_Event/create)]
     (try
       (SDLVideo/SDL_GL_MakeCurrent window gl-context)
       (SDLVideo/SDL_GL_SetSwapInterval 1) ;; enable vsync
       (SDLVideo/SDL_SetWindowPosition window SDLVideo/SDL_WINDOWPOS_CENTERED SDLVideo/SDL_WINDOWPOS_CENTERED)
       (SDLVideo/SDL_ShowWindow window)

       (loop [game {:total-time 0}]
         (while (SDLEvents/SDL_PollEvent event)
           (when (= (.type event) SDLEvents/SDL_EVENT_QUIT)
             (reset! running?* false)))
         (when (and @running?* (not (and (some? stop-flag*) @stop-flag*)))
           (let [ts   (SDLTimer/SDL_GetTicks)
                 dt   (- ts (:total-time game))
                 game (assoc game
                             :delta-time dt
                             :total-time ts)]
             (SDLVideo/SDL_GL_SwapWindow window)
             (recur game))))
       (finally
         (SDLVideo/SDL_GL_DestroyContext gl-context)
         (SDLVideo/SDL_DestroyWindow window)
         (SDLInit/SDL_Quit))))))
