(ns minusone.rules.anime.keyframe 
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::inp number?)
(s/def ::value some?)
(s/def ::anime-fn fn?)

; intermediate
(s/def ::next-inp number?)
(s/def ::next-out some?)
