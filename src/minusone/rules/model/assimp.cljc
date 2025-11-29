(ns minusone.rules.model.assimp
  (:require
   [clojure.spec.alpha :as s]))

;; coll of model files path, since models can require more than one file
(s/def ::model-to-load (s/coll-of string?))
(s/def ::gltf map?)
(s/def ::bins vector?)

