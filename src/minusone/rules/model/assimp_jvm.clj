(ns minusone.rules.model.assimp-jvm 
  (:require
   [engine.world :as world]
   [minusone.rules.model.assimp :as assimp]
   [odoyle.rules :as o]))

;; to make jvm side compile (I think this can be put to cljc but I'll decide after we investigate assimp in jvm)
(def rules
  (o/ruleset
   {::assimp/load-with-assimp
    [:what [:any ::assimp/model-to-load any]]

    ::assimp/gl-texture-to-load
    [:what [:any ::assimp/gltf any]]}))

(def system
  {::world/rules rules})