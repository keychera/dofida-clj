(ns minusone.rules.gl.shader
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [clojure.spec.alpha :as s]
   [engine.macros :refer [vars->map]]
   [engine.world :as world]
   [iglu.core :as iglu]
   [odoyle.rules :as o]
   [play-cljc.gl.utils :as gl-utils]))

(s/def ::context map?) ;; this is a map from play-cljc.gl.core/->game
(s/def ::program-data map?)

(def glsl-version #?(:clj "330" :cljs "300 es"))

(def default-vert-shader
  {:precision  "mediump float"
   :inputs     '{a_pos vec3
                 a_uv vec2}
   :outputs    '{uv vec2}
   :uniforms   '{u_mvp mat4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position (* u_mvp (vec4 a_pos "1.0")))
           (= uv a_uv))}})

(def default-frag-shader
  {:precision  "mediump float"
   :inputs     '{uv vec2}
   :outputs    '{o_color vec4}
   :uniforms   '{u_tex sampler2D}
   :signatures '{main ([] void)}
   :functions
   '{main ([] (= o_color (texture u_tex uv)))}})

(defn gather-locs [game program iglu-vert-shader iglu-frag-shader]
  (let [both-shader [iglu-vert-shader iglu-frag-shader]
        attr-locs   (->> (transduce (map :inputs) merge both-shader)
                         (into {} (map (fn [[attr-name]] [attr-name (gl game getAttribLocation program (str attr-name))]))))
        uni-locs    (->> (transduce (map :uniforms) merge both-shader)
                         (into {} (map (fn [[uni-name]] [uni-name (gl game getUniformLocation program (str uni-name))]))))]
    (vars->map attr-locs uni-locs)))

(defn create-program
  ([game] (create-program game default-vert-shader default-frag-shader))
  ([game iglu-vert-shader iglu-frag-shader]
   (let [vertex-source   (iglu/iglu->glsl (merge {:version glsl-version} iglu-vert-shader))
         fragment-source (iglu/iglu->glsl (merge {:version glsl-version} iglu-frag-shader))
         program         (gl-utils/create-program game vertex-source fragment-source)]
     (merge {:program program}
            (gather-locs game program iglu-vert-shader iglu-frag-shader)))))

(defn init-fn [world game]
  (-> world
      ;; we just realized that we complected this all these times :(
      (o/insert ::global ::context (select-keys game [:context]))))

(def system
  {::world/init-fn init-fn})