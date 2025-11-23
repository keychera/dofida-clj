(ns minusone.rules.view.firstperson
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [engine.world :as world]
   [minusone.rules.types :as types]
   [odoyle.rules :as o]
   [rules.time :as time]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(s/def ::position ::types/vec3)
(s/def ::front    ::types/vec3)
(s/def ::look-at  ::types/mat4)

(s/def ::move-control keyword?)
(s/def ::view-dx float?)
(s/def ::view-dy float?)

(def up (v/vec3 0.0 1.0 0.0)) ; y points towards the sky

(defn insert-player
  ([world position front]
   (o/insert world ::player {::position position ::front front})))

(def rules
  (o/ruleset
   {::firstperson-view
    [:what
     [::player ::position position]
     [::player ::front    front]
     :then
     (insert! ::player ::look-at (mat/look-at position (m/+ position front) up))]

    ::movement
    [:what
     [::time/now ::time/delta delta-time]
     [::player ::position position {:then false}]
     [::player ::front front {:then false}]
     [::player ::move-control control {:then false}]
     :then
     (let [speed 0.01
           right (m/normalize (m/cross front up))
           move  (case control
                   ::forward  (m/* front (* delta-time speed))
                   ::backward (m/* front (* delta-time speed -1))
                   ::strafe-l (m/* right (* delta-time speed -1))
                   ::strafe-r (m/* right (* delta-time speed))
                   nil)]
       (when move
         (insert! ::player {::position (m/+ position move)})))]}))

(def system
  {::world/rules rules})