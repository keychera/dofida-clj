(ns minustwo.systems.view.camera
  (:require
   [clojure.spec.alpha :as s]
   [engine.types :as types]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]
   [thi.ng.geom.matrix :as mat]))

(s/def ::position ::types/vec3)
(s/def ::view-matrix ::types/mat4)
(s/def ::projection-matrix ::types/mat4)

(defn init-fn [world game]
  (let [[w h]   (utils/get-size game)
        fov     45.0
        aspect  (/ w h)
        project (mat/perspective fov aspect 0.1 1000)]
    (o/insert world ::world/global ::projection-matrix project)))

(defn look-at-target [world cam-pos target-pos up]
  (o/insert world ::world/global {::position cam-pos ::view-matrix (mat/look-at cam-pos target-pos up)}))

(def system
  {::world/init-fn init-fn})