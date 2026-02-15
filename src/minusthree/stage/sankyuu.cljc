(ns minusthree.stage.sankyuu
  (:require
   [fastmath.core :as m]
   [fastmath.quaternion :as q]
   [fastmath.vector :as v]
   [minusthree.anime.anime :as anime]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.transform3d :as t3d]
   [minusthree.engine.world :as world :refer [esse]]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.model.assimp-lwjgl :refer [load-gltf-fn]]
   [minusthree.model.gltf-model :as gltf-model]
   [minusthree.model.pmx-model :as pmx-model :refer [load-pmx-fn]]
   [minusthree.stage.shaderdef :as shaderdef]
   [minustwo.gl.shader :as shader]))

;; 39

(defn init-fn [world _game]
  (let [ctx nil]
    (-> world
        (esse ::wolfie gltf-model/default
              (loading/push (load-gltf-fn ::wolfie "assets/models/SilverWolf/SilverWolf.pmx"))
              {::shader/program-info (cljgl/create-program-info-from-source ctx shaderdef/gltf-vert shaderdef/gltf-frag)
               ::t3d/translation (v/vec3 -5.0 0.0 -5.0)})
        (esse ::miku pmx-model/default
              (loading/push (load-pmx-fn ::miku "assets/models/HatsuneMiku/Hatsune Miku.pmx"))
              {::shader/program-info (cljgl/create-program-info-from-source ctx shaderdef/pmx-vert shaderdef/pmx-frag)})
        (esse ::wirebeing gltf-model/default
              (loading/push (load-gltf-fn ::wirebeing "assets/wirebeing.glb"))
              {::shader/program-info (cljgl/create-program-info-from-source ctx shaderdef/wirecube-vert shaderdef/wirecube-frag)}))))

(defn post-fn [world _game]
  (-> world
      (esse ::be-cute
            {::anime/duration 1600
             ::anime/bone-animes
             [{"右腕"
               {:rotation
                [{:in 0.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.5 :out (q/rotation-quaternion (m/radians 30.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 1.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}]}}
              ;; we can actually make this in one map, but I am currently hammock-ing about
              ;; how to compose this in a way that time is defined first
              {"左腕"
               {:rotation
                [{:in 0.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.25 :out (q/rotation-quaternion (m/radians -30.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.5 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 0.75 :out (q/rotation-quaternion (m/radians -30.0) (v/vec3 0.0 0.0 1.0))}
                 {:in 1.0 :out (q/rotation-quaternion (m/radians 0.0) (v/vec3 0.0 0.0 1.0))}]}}]})
      (esse ::wolfie {::anime/use ::be-cute})
      (esse ::wirebeing {::t3d/translation (v/vec3 -5.0 8.0 0.0)})))

(def system
  {::world/init-fn #'init-fn
   ::world/post-fn #'post-fn})
