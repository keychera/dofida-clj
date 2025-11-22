(ns minusone.rules.gl.vao
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [minusone.rules.gl.shader :as shader]
   [odoyle.rules :as o]
   [play-cljc.gl.utils :as gl-utils]))

(s/def ::vao any?)
(s/def ::buffers vector?)

(defn create-vao-from-buffer
  "create vao and bind some buffers
   `buffers` is seq of map of {:attr :data :type :size}"
  [game program-data buffers]
  (let [vao (gl game #?(:clj genVertexArrays :cljs createVertexArray))]
    (gl game bindVertexArray vao)
    (doseq [buf buffers]
      (let [attr-locs   (:attr-locs program-data)
            buffer      (gl-utils/create-buffer game)
            attr-loc    (get attr-locs (:attr buf))
            buffer-type (or (:buffer-type buf) (gl game ARRAY_BUFFER))]
        (when attr-loc (gl game enableVertexAttribArray attr-loc))
        (gl game bindBuffer buffer-type buffer)
        (gl game bufferData buffer-type (:data buf) (gl game STATIC_DRAW)) 
        (when attr-loc (gl game vertexAttribPointer attr-loc (:size buf) (:type buf) false 0 0))))
    (gl game bindVertexArray #?(:clj 0 :cljs nil))
    vao))

(def rules
  (o/ruleset
   {::create-vao
    [:what
     [::shader/global ::shader/context context]
     [esse-id ::shader/program-data program-data]
     [esse-id ::buffers buffers]
     :then
     (let [vao (create-vao-from-buffer context program-data buffers)]
       (s-> session
            (o/retract esse-id ::buffers)
            (o/insert esse-id ::vao vao)))]}))

(def system
  {::world/rules rules})