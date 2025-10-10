(ns engine.engine
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [engine.refresh :refer [*refresh?]]
   [engine.utils :as utils]
   [iglu.core :as iglu]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.utils :as gl-utils]))

;; from the very beginning https://www.opengl-tutorial.org/beginners-tutorials/tutorial-2-the-first-triangle/#shaders

(def glsl-version #?(:clj "330" :cljs "300 es"))

(defn ->game [context]
  (merge
   (c/->game context)
   {::naive (volatile! {})}))

(def triangle-data
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [-1.0 -1.0 0.0
    1.0 -1.0 0.0
    0.0  1.0 0.0]))

(def vertex-shader
  {:precision  "mediump float"
   :inputs     '{a_vertex_pos vec3}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position (vec4 a_vertex_pos "1.0")))}})

(def fragment-shader
  {:precision  "mediump float"
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([] (= o_color (vec4 "1.0")))}})

(defn init [game]
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))

  (let [vao (gl game #?(:clj genVertexArrays :cljs createVertexArray))]
    (vswap! (::naive game) assoc :vao vao))

  (let [vertex-source    (iglu/iglu->glsl (merge {:version glsl-version} vertex-shader))
        fragment-source  (iglu/iglu->glsl (merge {:version glsl-version} fragment-shader))
        triangle-program (gl-utils/create-program game vertex-source fragment-source)
        triangle-buffer  (gl-utils/create-buffer game)
        _                (gl game bindBuffer (gl game ARRAY_BUFFER) triangle-buffer)
        _                (gl game bufferData (gl game ARRAY_BUFFER) triangle-data (gl game STATIC_DRAW))
        attr-name        (-> vertex-shader :inputs keys first str)
        vertex-attr-loc  (gl game getAttribLocation triangle-program attr-name)]
    (vswap! (::naive game) assoc
            :program  triangle-program
            :vbo      triangle-buffer
            :loc      vertex-attr-loc)))

#?(:cljs
   (defn make-limited-logger [limit]
     (let [counter (atom 0)]
       (fn [err & args]
         (let [messages (apply str args)]
           (when (< @counter limit)
             (js/console.error (.-stack err))
             (swap! counter inc))
           (when (= @counter limit)
             (println "[SUPRESSED]" messages)
             (swap! counter inc)))))))

#?(:cljs (def log-once (make-limited-logger 4)))

(defn tick [game]
  (if @*refresh?
    (try (println "calling (init game)")
         (swap! *refresh? not)
         (init game)
         #?(:clj  (catch Exception err (throw err))
            :cljs (catch js/Error err (log-once err "[init-error] "))))
    (try
      (let [{:keys [::naive]} game
            {:keys [vao program vbo loc]} @naive
            [game-width game-height] (utils/get-size game)]
        (gl game viewport 0 0 game-width game-height)

        
        (gl game bindVertexArray vao)

        ;; triangle
        (gl game useProgram program)
        (gl game enableVertexAttribArray loc)
        (gl game bindBuffer (gl game ARRAY_BUFFER) vbo)
        (gl game vertexAttribPointer loc 3 (gl game FLOAT) false 0 0)
        (gl game drawArrays (gl game TRIANGLES) 0 3)
        (gl game disableVertexAttribArray loc))
      
      #?(:clj  (catch Exception err (throw err))
         :cljs (catch js/Error err (log-once err "[init-error] ")))))
  game)
