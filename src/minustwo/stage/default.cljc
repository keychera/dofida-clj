(ns minustwo.stage.default
  (:require
   [engine.world :as world]
   [minustwo.systems.view.camera :as camera]
   [thi.ng.geom.vector :as v]
   [minustwo.systems.view.firstperson :as firstperson]))

(defn init-fn [world _game]
  (-> world
      (firstperson/insert-fps-cam (v/vec3 0.0 10.5 8.0) (v/vec3 0.0 0.0 -1.0))
      (camera/look-at-target ::world/global (v/vec3 0.0 10.0 0.0) (v/vec3 0.0 10.0 -5.0) (v/vec3 0.0 1.0 0.0))))

(def system
  {::world/init-fn #'init-fn})
