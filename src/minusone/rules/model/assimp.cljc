(ns minusone.rules.model.assimp
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::model-to-load vector?)
(s/def ::gltf map?)
(s/def ::bins vector?)

