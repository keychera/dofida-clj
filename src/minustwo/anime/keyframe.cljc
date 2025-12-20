(ns minustwo.anime.keyframe
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::inp number?)
(s/def ::out some?)
(s/def ::anime-fn fn?)

(s/def ::next-inp number?)
(s/def ::next-out some?)

(s/def ::keyframe (s/keys :req [::inp ::out ::next-inp ::next-out ::anime-fn]))
(s/def ::keyframes (s/coll-of ::keyframe :kind vector?))