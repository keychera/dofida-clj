(ns minustwo.stage.pseudo.bones
  (:require
   [clojure.spec.alpha :as s]
   [engine.math :as m-ext]
   [engine.world :as world]
   [minustwo.model.pmx-model :as pmx-model]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

;; inspiration mostly from https://www.youtube.com/watch?v=q18Rhjgwqek
(s/def ::db* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))

(defn init-fn [world _game]
  (o/insert world ::world/global ::db* (atom {})))

(defn after-load-fn [world _game]
  (o/insert world ::world/global ::db* (atom {})))

(def osc-ps (* Math/PI 2 3.9))
(def damp 1.0)
(def damp-time 0.5)
(def damp-ratio (/ (Math/log damp) (* -1 osc-ps damp-time)))

(defn zero-if-small [n]
  (if (< (Math/abs n) 1e-12) 0.0 n))

(defn jiggle [bone bones-db* dt global-transform]
  (let [bones-db      @bones-db*
        fk-pos        (m-ext/m44->trans-vec3 global-transform)
        a             (* -2.0 dt damp-ratio osc-ps)
        b             (* dt osc-ps osc-ps)
        [tx ty tz]    fk-pos
        [cx cy cz]    (or (get-in bones-db [(:idx bone) :jiggle/position]) fk-pos)
        [vx vy vz]    (or (get-in bones-db [(:idx bone) :jiggle/velocity]) (v/vec3))
        velocity'     (v/vec3 (zero-if-small (+ vx (* a vx) (* b (- tx cx))))
                              (zero-if-small (+ vy (* a vy) (* b (- ty cy))))
                              (zero-if-small (+ vz (* a vz) (* b (- tz cz)))))
        [vx' vy' vz'] velocity'
        position'     (v/vec3 (zero-if-small (+ cx (* dt vx')))
                              (zero-if-small (+ cy (* dt vy')))
                              (zero-if-small (+ cz (* dt vz'))))]
    (swap! bones-db*
           (fn [j]
             (assoc j (:idx bone)
                    {:jiggle/position  position'
                     :jiggle/transform (m-ext/translation-mat position')
                     :jiggle/velocity  velocity'})))
    (assoc bone :global-transform global-transform)))

;; click@ remove# undress$ love me%

(defn bone-transducer [bones-db* dt]
  (fn [rf]
    (let [parents! (volatile! {})]
      (fn
        ([] (rf))
        ([result]
         (rf result))
        ([result {:keys [idx parent-bone-idx jiggle?] :as bone}]
         (let [local-trans  (pmx-model/calc-local-transform bone)
               parent       (get @parents! parent-bone-idx)
               parent-gt    (:global-transform parent)
               global-trans (if parent
                              (m/* parent-gt local-trans)
                              local-trans)
               updated-bone (if jiggle?
                              (jiggle bone bones-db* dt global-trans)
                              (assoc bone :global-transform global-trans))]
           (vswap! parents! assoc idx updated-bone)
           (rf result updated-bone)))))))

(defn resolve-gt [bones-db* bone]
  (if (:jiggle? bone)
    (get-in @bones-db* [(:idx bone) :jiggle/transform])
    (:global-transform bone)))

(def system
  {::world/init-fn #'init-fn
   ::world/after-load-fn #'after-load-fn})
