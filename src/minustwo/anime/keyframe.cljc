(ns minustwo.anime.keyframe
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::inp number?)
(s/def ::out some?)
(s/def ::anime-fn fn?)

(s/def ::next-inp number?)
(s/def ::next-out some?)

(s/def ::raw-keyframe (s/cat :inp ::inp :out ::out :anime-fn ::anime-fn))
(s/def ::keyframe (s/keys :req [::inp ::out ::next-inp ::next-out ::anime-fn]))
(s/def ::keyframes (s/coll-of ::keyframe :kind vector?))

(defn interpolate
  [keyframes]
  (->> (cycle keyframes)
       (take (inc (count keyframes)))
       (partition 2 1)
       (map (fn [[start-kf next-kf]]
              (s/assert ::raw-keyframe start-kf)
              (s/assert ::raw-keyframe next-kf)
              (let [[input output] start-kf
                    [next-input next-output anime-fn] next-kf]
                {::inp input
                 ::out output
                 ::next-inp next-input
                 ::next-out next-output
                 ::anime-fn anime-fn})))
       butlast))
