(ns minustwo.anime.morph
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [minustwo.systems.time :as time]
   [odoyle.rules :as o]))

(s/def ::morph-name string?)
(s/def ::interpolate number?)
(s/def ::active (s/map-of ::morph-name ::interpolate))

(s/def ::position-arr! some?)
(s/def ::bucket map?)

;; we realized that we have some these def duplicated
(s/def ::vertex-idx int?)
(s/def ::bone-idx int?)
(s/def ::material-idx int?)
(s/def ::translation vector?)
(s/def ::rotation vector?)
(s/def ::floats vector?)

(s/def ::offset (s/or :vertex-offset   (s/keys :req-un [::vertex-idx ::translation])
                      ;; we forgot that our data have this, spec is so cool
                      :bone-offset     (s/keys :req-un [::bone-idx ::translation ::rotation])
                      :uv-offset       (s/keys :req-un [::vertex-idx ::floats])
                      :material-offset (s/keys :req-un [::material-idx])))
(s/def ::offset-coll (s/coll-of ::offset))
(s/def ::deltas (s/map-of ::vertex-idx ::translation))
(s/def ::morph (s/keys :req-un [::morph-name ::offset-coll]))
(s/def ::morph-data (s/coll-of ::morph))

(defn init-fn [world _game]
  (-> world
      (o/insert ::world/global ::bucket {})))

(defn accumulate-morph-delta [active-mapping morph-data]
  (transduce
   (comp (filter #(contains? active-mapping (:morph-name %)))
         (map (fn [{:keys [morph-name offset-coll]}]
                (let [interpolate (active-mapping morph-name)]
                  (into []
                        (map (fn [offset] (update offset :translation (fn [t] (mapv #(* % interpolate) t)))))
                        offset-coll)))))
   (fn
     ([] {})
     ([acc] acc)
     ([acc next-offset]
      (reduce
       (fn [acc {:keys [vertex-idx translation]}]
         (if (contains? acc vertex-idx)
           (update acc vertex-idx #(mapv + % translation))
           (assoc acc vertex-idx translation)))
       acc next-offset)))
   morph-data))

(def rules
  (o/ruleset
   {::deltas
    [:what
     [::time/now ::time/slice 3]
     [esse-id ::active active-morphs]
     [esse-id ::morph-data morph-data]
     :then
     (let [active-mapping (into {} active-morphs)
           morph-deltas   (accumulate-morph-delta active-mapping morph-data)]
       (s-> session (o/insert esse-id ::deltas morph-deltas)))]

    ::cpu-morph
    [:what
     [esse-id ::deltas deltas]
     [esse-id ::position-arr! ^floats pos! {:then false}]
     [::world/global ::bucket bucket {:then false}]
     :then
     (let [bucket' (reduce
                    (fn [bucket' [vert-idx trans-v]]
                      (let [idx        (* vert-idx 3)
                            bucket-v   (get-in bucket' [esse-id vert-idx])
                            orig-v     (or bucket-v [(aget pos! idx) (aget pos! (+ idx 1)) (aget pos! (+ idx 2))])
                            bucket'    (cond-> bucket'
                                         (nil? bucket-v) (assoc-in [esse-id vert-idx] orig-v))
                            [x' y' z'] (mapv (fn [v v'] (+ v v')) orig-v trans-v)]
                        (aset pos! idx (float x'))
                        (aset pos! (+ idx 1) (float y'))
                        (aset pos! (+ idx 2) (float z'))
                        bucket'))
                    bucket deltas)]
       (s-> session (o/insert ::world/global ::bucket bucket')))]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})

;; maybe you didn't see that. the answer is not here. 
;; the answer is maybe here in some cases, but the lie is that nothing is ever here
;; the text above is the absolute truth. lies.

(comment
  (let [morph-data [{:morph-name "困る"
                     :offset-coll [{:vertex-idx 0 :translation [1.0 0.0 0.0]}
                                   {:vertex-idx 1 :translation [1.0 0.0 0.0]}]}
                    {:morph-name "にこり"
                     :offset-coll [{:vertex-idx 0 :translation [0.0 1.0 0.0]}
                                   {:vertex-idx 1 :translation [1.0 0.0 0.0]}
                                   {:vertex-idx 2 :translation [1.0 0.0 0.0]}]}
                    {:morph-name "怒り"
                     :offset-coll [{:vertex-idx 0 :translation [-99.0 0.0 0.0]}
                                   {:vertex-idx 1 :translation [-99.0 0.0 0.0]}
                                   {:vertex-idx 2 :translation [-99.0 0.0 0.0]}]}]
        deltas     (accumulate-morph-delta {"困る" 0.5 "にこり" 0.777} morph-data)]
    (s/assert ::morph-data morph-data)
    (s/assert ::deltas deltas)
    (assert
     (= deltas
        {0 [0.5 0.777 0.0], 1 [1.2770000000000001 0.0 0.0], 2 [0.777 0.0 0.0]}))
    deltas)

  :-)