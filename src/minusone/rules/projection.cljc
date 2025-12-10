(ns minusone.rules.projection
  (:require
   [clojure.spec.alpha :as s]
   [engine.utils :as utils]
   [engine.world :as world] 
   [minusone.rules.types :as types]
   [odoyle.rules :as o]
   [thi.ng.geom.matrix :as mat]))

(s/def ::matrix ::types/mat4)

(defn init-fn [world game]
  (let [[w h]   (utils/get-size game)
        fov     45.0
        aspect  (/ w h)
        project (mat/perspective fov aspect 0.1 1000)]
    (o/insert world ::world/global ::matrix project)))

(def system
  {::world/init-fn init-fn})