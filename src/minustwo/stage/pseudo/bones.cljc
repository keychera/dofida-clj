(ns minustwo.stage.pseudo.bones
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.math :as m-ext]
   [engine.world :as world]
   [minustwo.gl.geom :as geom]
   [minustwo.model.pmx-model :as pmx-model]
   [minustwo.systems.time :as time]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.core :as g]))

;; inspiration mostly from https://www.youtube.com/watch?v=q18Rhjgwqek
(s/def ::db* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))
(s/def ::prep-tail? boolean?)

(defn init-fn [world _game]
  (-> world
      (o/insert ::world/global ::db* (atom {}))
      (o/insert ::world/global ::prep-tail? true)))

(defn after-load-fn [world _game]
  (init-fn world _game))

(defn local-rotate 
  "helper for bone local rotation"
  ([{:keys [x y z alt-x-axis alt-y-axis alt-z-axis]}]
   (fn bone-local-rotate-fn [{:keys [bone-data]}]
     (let [{:keys [x-axis-vector z-axis-vector]} bone-data
           x-axis-vector (or alt-x-axis (some-> x-axis-vector v/vec3))
           z-axis-vector (or alt-z-axis (some-> z-axis-vector v/vec3))
           y-axis-vector (when y (or alt-y-axis (m/cross x-axis-vector z-axis-vector)))]
       (transduce (filter some?) m-ext/quat-mul-reducer
                  [(when x (q/quat-from-axis-angle x-axis-vector (m/radians x)))
                   (when z (q/quat-from-axis-angle z-axis-vector (m/radians z)))
                   (when y (q/quat-from-axis-angle y-axis-vector (m/radians y)))])))))

(def default-osc-ps (* Math/PI 2 3.0))
(def default-damp 0.5)
(def default-damp-time 0.05)

(defn jiggle [bone bones-db* dt self-gt]
  (let [bones-db    @bones-db*
        conf        (:jiggle? bone)
        ;; config
        osc-ps      (or (:osc-ps conf) default-osc-ps)
        damp        (or (:damp conf) default-damp)
        damp-time   (or (:damp-time conf) default-damp-time)
        damp-ratio  (/ (Math/log damp) (* -1 default-osc-ps damp-time))
        chain?      (:chain? conf)

        decom       (m-ext/decompose-matrix44 self-gt)
        head-pos    (:translation decom)
        head-rot    (:rotation decom)
        tail-fn     (:tail-fn bone)

        target-tail (m/+ (or (:tail bone) (v/vec3)) head-pos)
        target-tail (if tail-fn (tail-fn target-tail) target-tail)
        curr-tail   (or (get-in bones-db [(:idx bone) :jiggle/curr-tail]) target-tail)
        tail-vel    (or (get-in bones-db [(:idx bone) :jiggle/tail-vel]) (v/vec3))

        ;; semi implicit euler
        a           (* -2.0 dt damp-ratio osc-ps)
        b           (* dt osc-ps osc-ps)
        tail-vel'   (m/+ tail-vel (m/+ (m/* tail-vel a) (m/* (m/- target-tail curr-tail) b)))
        curr-tail'  (m/+ curr-tail (m/* tail-vel' dt))

        ray-tail    (m/- target-tail head-pos)
        ray-curr    (m/- curr-tail' head-pos)
        axis        (m/cross ray-tail ray-curr)
        angle       (Math/atan2 (m/mag axis) (m/dot ray-tail ray-curr))
        jiggle-quat (q/quat-from-axis-angle axis angle)
        trans-mat   (m-ext/vec3->trans-mat head-pos)
        rot-quat    (m/* jiggle-quat head-rot)
        rot-mat     (g/as-matrix rot-quat)
        transform'  (m/* trans-mat rot-mat)]
    (swap! bones-db*
           (fn [j]
             (assoc j (:idx bone) {:jiggle/curr-tail curr-tail'
                                   :jiggle/tail-vel  tail-vel'
                                   :jiggle/transform transform'})))
    (assoc bone :global-transform (if chain? transform' self-gt))))

(defn prep-tails [transform-tree]
  (->> (rseq transform-tree)
       (into []
             (fn [rf]
               (let [children! (volatile! {})]
                 (fn
                   ([] (rf))
                   ([result] (rf result))
                   ([result bone]
                    (vswap! children! assoc (:idx bone) bone)
                    (if-let [tail (when (:jiggle? bone)
                                    (or (some-> (get @children! (-> bone :bone-data :connection)) :translation)
                                        (some-> bone :bone-data :position-offset
                                                pmx-model/pmx-coord->opengl-coord
                                                v/vec3)))]
                      (rf result (assoc bone :tail tail))
                      (rf result bone)))))))
       (rseq)
       (into [])))

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

(def rules
  (o/ruleset
   {::set-jiggle-tail
    [:what
     [::time/now ::time/slice 1]
     [::world/global ::prep-tail? true]
     [esse-id ::geom/transform-tree transform-tree {:then false}]
     :then
     (let [transform-tree' (prep-tails transform-tree)]
       (println "[bones] prepping tails for jiggling")
       (s-> session
            (o/insert ::world/global ::prep-tail? false)
            (o/insert esse-id ::geom/transform-tree transform-tree')))]}))

(def system
  {::world/init-fn #'init-fn
   ::world/after-load-fn #'after-load-fn
   ::world/rules #'rules})
