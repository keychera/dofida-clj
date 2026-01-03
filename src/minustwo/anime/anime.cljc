(ns minustwo.anime.anime
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [minustwo.anime.keyframe :as keyframe]
   [minustwo.anime.pacing :as pacing]
   [minustwo.systems.uuid-instance :as inst :refer [esse-inst]]
   [odoyle.rules :as o]))

(s/def ::timeline (s/coll-of ::keyframe/raw-keyframe :kind vector?))
(s/def ::kfs (s/coll-of ::keyframe/keyframes :kind vector?))
(s/def ::origin-val some?)
(s/def ::anime (s/keys :req-un [::origin-val ::timeline]))
(s/def ::attr->anime (s/map-of ::o/attr ::anime))
(s/def ::animating ::o/attr)

(s/def ::db* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))

(defn init-fn [world _game]
  (o/insert world ::world/global ::db* (atom {})))

(defn insert [world esse-id attr->anime]
  (o/insert world esse-id ::attr->anime attr->anime))

(def rules
  (o/ruleset
    {::attr-keyframes
     [:what
      [esse-id ::attr->anime attr->anime]
      [::world/global ::db* attr-kfs-db*]
      :then
      (s-> (reduce
             (fn [s' [attr anime]]
               (let [timeline (:timeline anime)
                     kfs      (keyframe/interpolate timeline)]
                 (swap! attr-kfs-db* assoc-in [esse-id attr]
                   {::kfs kfs
                    ::origin-val (:origin-val anime)}))
               (esse-inst s' esse-id {::animating attr}))
             session attr->anime)
        (o/retract esse-id ::attr->anime))]

     ::anime-interpolate
     [:what
      [::world/global ::pacing/progress progress]
      [esse-uuid ::inst/origin-id esse-id]
      [esse-uuid ::animating attr]
      [::world/global ::db* attr-anime-db*]
      :then
      (let [data  (get-in @attr-anime-db* [esse-id attr])
            orig  (::origin-val data)
            anime (into []
                    (comp
                      (filter (fn [{::keyframe/keys [inp next-inp]}] (and (>= progress inp) (< progress next-inp))))
                      (map (fn [{::keyframe/keys [inp next-inp out next-out anime-fn]}]
                             (let [t            (/ (- progress inp) (- next-inp inp))
                                   interpolated (anime-fn t orig out next-out)]
                               [esse-id attr interpolated]))))
                    (::kfs data))]
        (s-> (reduce o/insert session anime)))]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})

(comment
  (s/assert
    ::esse-anime
    {::camera
     {::an-attr
      [[0.0 10.0 (fn [t orig out next-out] (vector t orig out next-out))]
       [0.0 10.0 (fn [t orig out next-out] (vector t orig out next-out))]
       [0.0 10.0 (fn [t orig out next-out] (vector t orig out next-out))]]}})

  :-)
