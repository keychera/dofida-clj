(ns minustwo.anime.pose
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! s->]]
   [engine.math :as m-ext]
   [engine.world :as world]
   [engine.xform :as xform]
   [minustwo.anime.keyframe :as keyframe]
   [minustwo.anime.pacing :as pacing]
   [minustwo.gl.geom :as geom]
   [minustwo.systems.time :as time]
   [odoyle.rules :as o]
   [thi.ng.math.core :as m]))

(s/def ::pose-xform fn?)
(s/def ::pose-tree ::geom/transform-tree)

(s/def ::point-in-time (s/cat :time-inp ::keyframe/inp
                              :pose-fn ::pose-xform
                              :anime-fn fn?))
(s/def ::timeline (s/coll-of ::point-in-time :kind vector?))
(s/def ::stop-anime boolean?)

(s/def ::transform-tree-fn fn?)
(s/def ::kfs ::keyframe/keyframes)

(def default {::pose-xform identity})

(defn strike [a-pose]
  {::stop-anime true
   ::pose-xform a-pose})

(defn mark [transform-tree-fn]
  {::transform-tree-fn transform-tree-fn})

(defn anime
  ([timeline] (anime timeline {}))
  ([timeline {:keys [relative?]}]
   (let [timeline' (if relative?
                     (into [] xform/accumulate-time timeline)
                     timeline)]
     (s/assert ::timeline timeline')
     {::timeline timeline'})))

(defn do-pose [pose-fn]
  (map (fn [{:keys [name] :as bone}]
         (if-let [bone-pose (pose-fn name)]
           (let [next-translation (:t bone-pose)
                 next-rotation    (or (:r bone-pose)
                                      (when (:r-fn bone-pose) ((:r-fn bone-pose) bone)))]
             (cond-> bone
               next-translation (update :translation m/+ next-translation)
               next-rotation (update :rotation m/* next-rotation)))
           bone))))

(s/def ::db* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))

(defn init-fn [world _game]
  (o/insert world ::world/global ::db* (atom {})))

(defn interpolate-pose [pose-a pose-b t]
  (into []
        (map (fn [[bone-a bone-b]]
               (let [trans-a    (:translation bone-a)
                     rotate-a   (:rotation bone-a)
                     scale-a    (:scale bone-a)
                     trans-b    (:translation bone-b)
                     rotate-b   (:rotation bone-b)
                     scale-b    (:scale bone-b)
                     int-trans  (m/mix trans-a trans-b t)
                     int-rotate (m-ext/quat-mix rotate-a rotate-b t)
                     int-scale  (when scale-a (m/mix scale-a scale-b t))]
                 (cond-> (assoc bone-b
                                :translation int-trans
                                :rotation int-rotate)
                   int-scale (assoc :scale int-scale)))))
        (map vector pose-a pose-b)))

(def rules
  (o/ruleset
   {::pose-keyframes
    [:what
     [esse-id ::timeline timeline]
     [::world/global ::db* keyframes-db*]
     :then
     (let [kfs (keyframe/interpolate timeline)]
       (when (seq kfs)
         (swap! keyframes-db* assoc-in [esse-id ::kfs] kfs)))
     (s-> session (o/retract esse-id ::timeline))]

    ::pose-for-the-fans!
    [:what
     [::time/now ::time/slice 0]
     [esse-id ::pose-xform pose-xform]
     [esse-id ::geom/transform-tree transform-tree]
     [::world/global ::db* keyframes-db*]
     :then
     (let [pose-tree (into [] pose-xform transform-tree)]
       (s-> session
            (o/retract esse-id ::pose-xform)
            (o/insert esse-id ::pose-tree pose-tree)))]

    ::mutate-transform-tree
    [:what
     [::time/now ::time/slice 0]
     [esse-id ::transform-tree-fn transform-tree-fn]
     [esse-id ::geom/transform-tree transform-tree {:then false}]
     :then
     (s-> session
          (o/insert esse-id ::geom/transform-tree (transform-tree-fn transform-tree))
          (o/retract esse-id ::transform-tree-fn))]

    ::stop-interpolation
    [:what ;; repl heuristic: striking a pose will stop pose-interpolation
     [esse-id ::stop-anime true]
     [::world/global ::db* keyframes-db*]
     :then (swap! keyframes-db* update esse-id dissoc ::kfs)]

    ::pose-interpolation
    [:what
     [::time/now ::time/total tt {:then false}]
     [::time/now ::time/slice 1]
     [esse-id ::geom/transform-tree transform-tree]
     [::world/global ::pacing/progress progress]
     [::world/global ::db* keyframes-db*]
     :then
     (let [kfs      (get-in @keyframes-db* [esse-id ::kfs])
           running  (eduction
                     (filter (fn [{::keyframe/keys [inp next-inp]}] (and (>= progress inp) (< progress next-inp))))
                     (map (fn [{::keyframe/keys [inp next-inp out next-out anime-fn]}]
                            (let [prev-pose (into [] out transform-tree)
                                  next-pose (into [] next-out transform-tree)
                                  t         (anime-fn (/ (- progress inp) (- next-inp inp)))]
                              (interpolate-pose prev-pose next-pose t))))
                     kfs)
           pose     (first running)]
       (when pose
         (insert! esse-id ::pose-tree pose)))]}))

(def system
  {::world/init-fn init-fn ::world/rules #'rules})

;; smells. 