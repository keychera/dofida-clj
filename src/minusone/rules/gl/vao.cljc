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
(s/def ::attributes map?)

(defn create-vao-from-attrs
  "create vao and bind some attributes.
   attributes is map of {attr-symbol -> {:data :type :size}}. 
   this follows play.cljc attributes."
  [game program-data attributes]
  (let [vao (gl game #?(:clj genVertexArrays :cljs createVertexArray))]
    (gl game bindVertexArray vao)
    (doseq [[attr-name attr] attributes]
      (let [attr-locs (:attr-locs program-data)
            vbo       (gl-utils/create-buffer game)
            loc       (get attr-locs attr-name)]
        (gl game bindBuffer (gl game ARRAY_BUFFER) vbo)
        (gl game bufferData (gl game ARRAY_BUFFER) (:data attr) (gl game STATIC_DRAW))
        (gl game enableVertexAttribArray loc)
        (gl game bindBuffer (gl game ARRAY_BUFFER) vbo)
        (gl game vertexAttribPointer loc (:size attr) (:type attr) false 0 0)))
    vao))

(def rules
  (o/ruleset
   {::create-vao
    [:what
     [::shader/global ::shader/context context]
     [esse-id ::shader/program-data program-data]
     [esse-id ::attributes attributes]
     :then
     (let [vao (create-vao-from-attrs context program-data attributes)]
       (s-> session
            (o/retract esse-id ::attributes)
            (o/insert esse-id ::vao vao)))]}))

(def system
  {::world/rules rules})