(ns minusone.rules.view.firstperson
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [engine.world :as world]
   [minusone.rules.types :as types]
   [odoyle.rules :as o]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(s/def ::position  ::types/vec3)
(s/def ::target    ::types/vec3)

(s/def ::direction ::types/vec3)
(s/def ::right     ::types/vec3)
(s/def ::look-at   ::types/mat4)

(defn calc-firstperson-view [position target]
  (let [direction (m/normalize (m/- position target))
        up        (v/vec3 0.0 1.0 0.0)
        right     (m/normalize (m/cross up direction))]
    {::direction direction
     ::right right
     ::look-at (mat/look-at position target up)}))

(defn insert-eye
  ([world position target]
   (o/insert world ::eye {::position position ::target target})))

(def rules
  (o/ruleset
   {::firstperson-view
    [:what
     [::eye ::position position]
     [::eye ::target   target]
     :then
     (insert! ::eye (calc-firstperson-view position target))]}))

(def system
  {::world/rules rules})