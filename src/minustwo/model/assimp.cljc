(ns minustwo.model.assimp
  (:require
   [clojure.spec.alpha :as s]))

;; coll of model files path, since models can require more than one file
(s/def ::model-to-load (s/coll-of string?))
(s/def ::tex-unit-offset int?)

;; this is a workaround for imported gltf that doesn't have DFS ordering of node hierarchy
;; there is an known error path here: if it is passed a name of an existing bone/node but not the true parent, it will throw error at render time (because some bones is not processed, null matrix)
(s/def ::node-parent-fix string?) 

(s/def ::config (s/keys :req-un [::tex-unit-offset]
                        :opt-un [::node-parent-fix]))
