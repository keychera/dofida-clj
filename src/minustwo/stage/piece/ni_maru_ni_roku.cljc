(ns minustwo.stage.piece.ni-maru-ni-roku
  (:require
   [engine.world :as world :refer [esse]]
   [minustwo.anime.IK :as IK]
   [minustwo.anime.morph :as morph]
   [minustwo.anime.pacing :as pacing]
   [minustwo.anime.pose :as pose]
   [minustwo.gl.shader :as shader]
   [minustwo.model.pmx-model :as pmx-model]
   [minustwo.stage.pmx-renderer :as pmx-renderer]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.view.firstperson :as firstperson]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(def absolute-cinema
  (comp
   (IK/IK-transducer1 "左腕" "左ひじ" "左手首" (v/vec3 -4.27 10.0 6.0))
   (IK/IK-transducer1 "右腕" "右ひじ" "右手首" (v/vec3 4.27 10.0 6.0))
   (IK/IK-transducer1 "左足D" "左ひざD" "左足首" (v/vec3 0.0 1.0 3.0)
                      (IK/make-IK-solver2 (v/vec3 1.0 0.0 0.0)))))

(defn init-fn [world _game]
  (-> world
      (esse ::silverwolf-pmx
            #::pmx-model{:model-path "assets/models/SilverWolf/SilverWolf.pmx"}
            #::shader{:use ::pmx-renderer/pmx-shader}
            t3d/default)))


(defn after-load-fn [world _game]
  (-> world
      (firstperson/insert-player (v/vec3 3.0 20.5 -3.0) (v/vec3 0.0 0.0 -1.0) (m/radians 110.0) (m/radians -35))
      (pacing/insert-timeline
       ;; hmmm this API is baaad, need more hammock, artifact first, construct later
       ::silverwolf-pmx
       [[0.0 [[::silverwolf-pmx ::morph/active {"笑い1" 0.0
                                                "にこり" 0.0
                                                "にやり3" 0.0}]]]
        [0.5 [[::silverwolf-pmx ::morph/active {"笑い1" 1.2
                                                "にこり" 0.5
                                                "にやり3" 0.5}]]]
        [7.5 [[::silverwolf-pmx ::morph/active {"笑い1" 0.0
                                                "にこり" 0.0
                                                "にやり3" 0.0}]]]])
      (esse ::silverwolf-pmx
            ;; (pose/strike absolute-cinema)
            (pose/anime
             [[0.0 identity identity]
              [0.5 absolute-cinema identity]
              [7.5 absolute-cinema identity]
              [8.0 identity identity]])
            #::t3d{:translation (v/vec3 0.0 0.0 0.0)
                   :rotation (q/quat-from-axis-angle (v/vec3 0.0 1.0 0.0) (m/radians 0.0))})))

(def system
  {::world/init-fn #'init-fn
   ::world/after-load-fn #'after-load-fn})
