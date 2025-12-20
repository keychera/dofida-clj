(ns minustwo.anime.pose
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [engine.world :as world]
   [minustwo.gl.gltf :as gltf]
   [odoyle.rules :as o]))

(s/def ::pose-xform fn?)
(s/def ::pose-tree ::gltf/transform-tree)

(def default {::pose-xform identity})

(defn strike [a-pose] {::pose-xform a-pose})

(def rules
  (o/ruleset
   {::pose
    [:what
     [esse-id ::pose-xform pose-xform]
     [esse-id ::gltf/transform-tree transform-tree]
     :then
     (let [pose-tree (into [] pose-xform transform-tree)]
       (insert! esse-id ::pose-tree pose-tree))]}))

(def system
  {::world/rules rules})