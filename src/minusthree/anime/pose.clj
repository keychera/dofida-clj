(ns minusthree.anime.pose
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [minusthree.engine.world :as world]
   [minusthree.gl.geom :as geom]
   [minusthree.gl.gltf :as gltf]
   [odoyle.rules :as o]))

(s/def ::pose-xform fn?)
(s/def ::pose-tree ::geom/transform-tree)

(def default {::pose-xform identity})

(defn strike [a-pose]
  {::pose-xform a-pose})

(def rules
  (o/ruleset
   {::pose-for-the-fans!
    [:what
     [esse-id ::pose-xform pose-xform]
     [esse-id ::geom/transform-tree transform-tree]
     :then
     (let [pose-tree (into [] (comp pose-xform gltf/global-transform-xf) transform-tree)]
       (insert! esse-id ::pose-tree pose-tree))]}))

(def system
  {::world/rules #'rules})
