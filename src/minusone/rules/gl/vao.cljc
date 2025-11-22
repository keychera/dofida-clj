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
(s/def ::entries vector?)

(defn create-vao-from-entries
  "create vao and do some bindings and pointing"
  [game program-data entries]
  (let [vao (gl game #?(:clj genVertexArrays :cljs createVertexArray))]
    (gl game bindVertexArray vao)
    (doseq [entry entries]
      ;; entry: buffer binding
      (when-let [data (:data entry)]
        (let [buffer      (gl-utils/create-buffer game)
              buffer-type (or (:buffer-type entry) (gl game ARRAY_BUFFER))]
          (gl game bindBuffer buffer-type buffer)
          (gl game bufferData buffer-type data (gl game STATIC_DRAW))))
      ;; entry: attrib pointing
      (when-let [attr-loc (get (:attr-locs program-data) (:attr entry))]
        (let [{:keys [size type stride offset]
               :or {stride 0 offset 0}} entry]
          (gl game enableVertexAttribArray attr-loc)
          (gl game vertexAttribPointer attr-loc size type false stride offset))))
    (gl game bindVertexArray #?(:clj 0 :cljs nil))
    vao))

(def rules
  (o/ruleset
   {::create-vao
    [:what
     [::shader/global ::shader/context context]
     [esse-id ::shader/program-data program-data]
     [esse-id ::entries entries]
     :then
     (let [vao (create-vao-from-entries context program-data entries)]
       (s-> session
            (o/retract esse-id ::entries)
            (o/insert esse-id ::vao vao)))]}))

(def system
  {::world/rules rules})