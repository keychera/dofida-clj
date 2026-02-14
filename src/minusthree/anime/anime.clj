(ns minusthree.anime.anime
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [fastmath.quaternion :as q]
   [fastmath.vector :as v]
   [minusthree.engine.time :as time]
   [minusthree.gl.geom :as geom]
   [minusthree.gl.gltf :as gltf]
   [odoyle.rules :as o]))

(s/def :translation/out #(instance? fastmath.vector.Vec3 %))
(s/def ::translation-kf (s/keys :req-un [::in :translation/out]))
(s/def ::translation-track (s/coll-of ::translation-kf :kind vector?))
(s/def ::translation ::translation-track)

(s/def :rotation/out #(instance? fastmath.vector.Vec4 %))
(s/def ::rotation-kf (s/keys :req-un [::in :rotation/out]))
(s/def ::rotation-track (s/coll-of ::rotation-kf :kind vector?))
(s/def ::rotation ::rotation-track)

(s/def ::channels
  (s/keys :opt-un [::translation ::rotation]))

(s/def ::bone-name string?)
(s/def ::bone-anime (s/map-of ::bone-name ::channels))

(comment
  ;; this will fail
  (s/assert ::bone-anime
            {"左手"
             {:translation
              [{:in 0.0 :out (v/vec3 0.0 0.0 0.0)}
               {:in 0.5 :out (v/vec3 1.0 1.0 1.0)}
               {:in 1.0 :out (q/quaternion 1.0)}]}})

  (let [bones   [{:translation (v/vec3 0.0 0.0 0.0) :name "右手"}
                 {:translation (v/vec3 1.0 1.0 1.0) :name "左手"}]
        anime-1 {"左手"
                 {:translation
                  [{:in 0.0 :out (v/vec3 0.0 0.0 0.0)}
                   {:in 0.5 :out (v/vec3 1.0 1.0 1.0)}
                   {:in 1.0 :out (v/vec3 0.0 0.0 0.0)}]}}
        anime-2 {"右手"
                 {:translation
                  [{:in 0.0 :out (v/vec3 0.0 0.0 0.0)}
                   {:in 0.3 :out (v/vec3 1.0 1.0 1.0)}
                   {:in 0.6 :out (v/vec3 0.0 0.0 0.0)}
                   {:in 1.0 :out (v/vec3 1.0 1.0 1.0)}]}}]
    (letfn [(progress [t t0 t1]
              (/ (- t t0) (- t1 t0)))

            (blend-kf-with [track blender-fn t]
              (let [[k0 k1] (->> (partition-all 2 1 track)
                                 (filter (fn [[k0 k1]] (<= (:in k0) t (:in k1))))
                                 first)
                    p       (progress t (:in k0) (:in k1))]
                (blender-fn (:out k0) (:out k1) p)))

            (blend-channels [channel t]
              (reduce-kv
               (fn [ch' ch-type track]
                 (cond-> ch'
                   (= :translation ch-type)
                   (assoc :translation (blend-kf-with track v/interpolate t))))
               {}
               channel))

            (do-anime [bones bone-animes t]
              (let [bone->sample (-> (apply merge bone-animes)
                                     (update-vals #(blend-channels % t)))]
                (into []
                      (map (fn [{:keys [name] :as bone}]
                             (let [sample (get bone->sample name)]
                               (cond-> bone
                                 (:translation sample) (update :translation v/add (:translation sample))))))
                      bones)))]
      (do-anime bones [anime-1 anime-2] 0.42))))
