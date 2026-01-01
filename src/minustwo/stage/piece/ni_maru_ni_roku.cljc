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
   [thi.ng.math.core :as m]
   [engine.math :as m-ext]
   [clojure.string :as str]))

(defn or-fn [& fns]
  (fn [arg]
    (reduce
     (fn [arg a-fn] (when (not (= :end a-fn))
                      (let [res (a-fn arg)] (if res (reduced res) arg))))
     arg (conj (into [] fns) :end))))

(defn do-pose [pose-fn]
  (map (fn [{:keys [name] :as bone}]
         (if-let [bone-pose (pose-fn name)]
           (let [next-translation (:t bone-pose)
                 next-rotation    (or (:r bone-pose)
                                      (when (:r-fn bone-pose) ((:r-fn bone-pose) bone)))]
             (cond-> bone
               next-translation (update :translation m/+ next-translation)
               next-rotation (update :rotation m/* next-rotation)))
           bone))))

(defn rotate-local-fn
  ([{:keys [x y z alt-x-axis alt-y-axis alt-z-axis]}]
   (fn bone-local-rotate-fn [{:keys [bone-data]}]
     (let [{:keys [x-axis-vector z-axis-vector]} bone-data
           x-axis-vector (or alt-x-axis (some-> x-axis-vector v/vec3))
           z-axis-vector (or alt-z-axis (some-> z-axis-vector v/vec3))
           y-axis-vector (when y (or alt-y-axis (m/cross x-axis-vector z-axis-vector)))]
       (transduce (filter some?) m-ext/quat-mul-reducer
                  [(when x (q/quat-from-axis-angle x-axis-vector (m/radians x)))
                   (when z (q/quat-from-axis-angle z-axis-vector (m/radians z)))
                   (when y (q/quat-from-axis-angle y-axis-vector (m/radians y)))])))))

(def absolute-cinema
  (comp
   (do-pose
    (or-fn
     {"左腕"  {:r-fn (rotate-local-fn {:y 25.0 :z -16.0})}
      "左ひじ" {:r-fn (rotate-local-fn {:x -100.0 :z -110.0})}
      "左手首" {:r-fn (rotate-local-fn {:y 15.0 :z 10.0})}}
     (fn kazoeru [name]
       (let [[matches? finger level] (re-matches #"左([親人中薬小])指([０１２３])" name)]
         (when matches?
           (let [wrist-x-axis (v/vec3 0.771123 -0.635213 0.043293) ;; the finger other than 親 doesn't have local-axis data, manually taken from 左手首
                 wrist-z-axis (v/vec3 -0.033414 0.027528 0.999062)
                 wrist-y-axis (m/cross wrist-x-axis wrist-z-axis)]
             (if (= "親" finger)
               {:r-fn (rotate-local-fn #_{:y -20} {:x 50.0 :z -10.0})}
               {:r (cond-> (q/quat)
                     (= "１" level)
                     (cond->
                      (= "薬" finger) (m/* (q/quat-from-axis-angle wrist-y-axis (m/radians 5.0)))
                      (= "小" finger) (m/* (q/quat-from-axis-angle wrist-y-axis (m/radians 30.0))))

                     (= "２" level)
                     (cond->
                      (= "中" finger) (m/* (q/quat-from-axis-angle wrist-x-axis (m/radians -10.0)))
                      (= "薬" finger) (m/* (q/quat-from-axis-angle wrist-x-axis (m/radians -25.0)))
                      (= "小" finger) (m/* (q/quat-from-axis-angle wrist-x-axis (m/radians -50.0))))

                     (= "3" level)
                     (cond->
                      (= "中" finger) (m/* (q/quat-from-axis-angle wrist-z-axis (m/radians -10.0)))
                      (= "薬" finger) (m/* (q/quat-from-axis-angle wrist-z-axis (m/radians -25.0)))
                      (= "小" finger) (m/* (q/quat-from-axis-angle wrist-z-axis (m/radians -50.0))))

                     (str/includes? #_"小" "中薬小" finger)
                     (cond->
                      (= "１" level) (m/* (q/quat-from-axis-angle wrist-z-axis (m/radians -70.0)))
                      (= "２" level) (m/* (q/quat-from-axis-angle wrist-z-axis (m/radians -90.0)))
                      (= "３" level) (m/* (q/quat-from-axis-angle wrist-z-axis (m/radians -90.0)))))})))))))
   (IK/IK-transducer1 "右腕" "右ひじ" "右手首" (v/vec3 4.27 10.0 6.0))
   (IK/IK-transducer1 "左足D" "左ひざD" "左足首" (v/vec3 0.0 1.0 3.0)
                      (IK/make-IK-solver2 (v/vec3 1.0 0.0 0.0)))))

(defn init-fn [world _game]
  (-> world
      (firstperson/insert-player (v/vec3 3.0 20.5 -3.0) (v/vec3 0.0 0.0 -1.0) (m/radians 110.0) (m/radians -35))
      (esse ::silverwolf-pmx
            #::pmx-model{:model-path "assets/models/SilverWolf/SilverWolf.pmx"}
            #::shader{:use ::pmx-renderer/pmx-shader}
            t3d/default)))


(defn after-load-fn [world _game]
  (-> world
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
            (pose/strike absolute-cinema)
            #_(pose/anime
               [[0.0 identity identity]
                [0.5 absolute-cinema identity]
                [7.5 absolute-cinema identity]
                [8.0 identity identity]])
            #::t3d{:translation (v/vec3 0.0 0.0 0.0)
                   :rotation (q/quat-from-axis-angle (v/vec3 0.0 1.0 0.0) (m/radians 0.0))})))

(def system
  {::world/init-fn #'init-fn
   ::world/after-load-fn #'after-load-fn})
