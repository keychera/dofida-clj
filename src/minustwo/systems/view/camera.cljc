(ns minustwo.systems.view.camera
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [engine.types :as types]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]
   [thi.ng.geom.matrix :as mat]))

(s/def ::position ::types/vec3)
(s/def ::look-at-target ::types/vec3)
(s/def ::up ::types/vec3)

(s/def ::active some?)

(s/def ::view-matrix ::types/mat4)
(s/def ::projection-matrix ::types/mat4)

(defn init-fn [world game]
  (let [[w h]   (utils/get-size game)
        fov     45.0
        aspect  (/ w h)
        project (mat/perspective fov aspect 0.1 1000)]
    (o/insert world ::world/global ::projection-matrix project)))

(defn activate-cam [world cam-id]
  (o/insert world ::world/global ::active cam-id))

(defn look-at-target
  ([world cam-pos target-pos up] (look-at-target world ::world/global cam-pos target-pos up))
  ([world cam-id cam-pos target-pos up]
   (-> world
       (o/insert cam-id {::position cam-pos ::look-at-target target-pos ::up up})
       (activate-cam cam-id))))

(def rules
  (o/ruleset
   {::update-camera
    [:what
     [esse-id ::position position]
     [esse-id ::look-at-target look-at-target]
     [esse-id ::up up]
     :then
     (insert! esse-id ::view-matrix (mat/look-at position look-at-target up))]}))

(def system
  {::world/init-fn init-fn
   ::world/rules #'rules})
