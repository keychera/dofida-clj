(ns minusone.rules.model.assimp
  (:require
   [clojure.spec.alpha :as s]))

;; coll of model files path, since models can require more than one file
(s/def ::model-to-load (s/coll-of string?))
(s/def ::gltf map?)
(s/def ::bins vector?)
(s/def ::tex-unit-offset int?)

;; rules name
(s/def ::load-with-assimp any?)
(s/def ::gl-texture-to-load any?)