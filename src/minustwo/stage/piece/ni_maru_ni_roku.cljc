(ns minustwo.stage.piece.ni-maru-ni-roku
  (:require
   [clojure.string :as str]
   [engine.macros :refer [s->]]
   [engine.math :as m-ext]
   [engine.math.easings :as easings]
   [engine.world :as world :refer [esse]]
   [minustwo.anime.morph :as morph]
   [minustwo.anime.pacing :as pacing]
   [minustwo.anime.pose :as pose]
   [minustwo.gl.shader :as shader]
   [minustwo.model.assimp :as assimp]
   [minustwo.model.pmx-model :as pmx-model]
   [minustwo.stage.gltf-renderer :as gltf-renderer]
   [minustwo.stage.pmx-renderer :as pmx-renderer]
   [minustwo.stage.wirecube :as wirecube]
   [minustwo.systems.time :as time]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.view.firstperson :as firstperson]
   [odoyle.rules :as o]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]
   [minustwo.stage.pseudo.particle :as particle]))

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

(defn hitung [angka]
  (fn kazoeru-fn [name]
    (let [[matches? finger level] (re-matches #"左([親人中薬小])指([０１２３])" name)]
      (when matches?
        (let [wrist-x-axis (v/vec3 0.771123 -0.635213 0.043293) ;; the finger other than 親 doesn't have local-axis data, manually taken from 左手首
              wrist-z-axis (v/vec3 -0.033414 0.027528 0.999062)
              wrist-y-axis (m/cross wrist-x-axis wrist-z-axis)]
          (if (= "親" finger)
            {:r-fn (rotate-local-fn (if (= angka 5) {:y -20} {:x 50.0 :z -10.0}))}
            {:r (cond-> (q/quat)
                  (= "１" level)
                  (cond->
                   (= "薬" finger) (m/* (q/quat-from-axis-angle wrist-y-axis (m/radians 5.0)))
                   (= "小" finger) (m/* (q/quat-from-axis-angle wrist-y-axis (m/radians 10.0))))

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

                  (str/includes? #_"小" (subs "人中薬小-" (mod angka 6)) finger)
                  (cond->
                   (= "１" level) (m/* (q/quat-from-axis-angle wrist-z-axis (m/radians -70.0)))
                   (= "２" level) (m/* (q/quat-from-axis-angle wrist-z-axis (m/radians -90.0)))
                   (= "３" level) (m/* (q/quat-from-axis-angle wrist-z-axis (m/radians -90.0)))))}))))))

(defn hand-counting [{:keys [angka factor]}]
  (comp
   (do-pose
    (or-fn
     {"左腕"  {:r-fn (rotate-local-fn {:y 25.0 :z -16.0})}
      "左ひじ" {:r-fn (rotate-local-fn {:x -100.0 :z (+ -110.0 (* 0.22 factor))})}
      "左手首" {:r-fn (rotate-local-fn {:y 15.0 :z factor})}}
     (hitung angka)))))

(defn init-fn [world _game]
  (-> world
      (firstperson/insert-player (v/vec3 3.0 20.5 -3.0) (v/vec3 0.0 0.0 -1.0) (m/radians 110.0) (m/radians -35))
      (esse ::silverwolf-pmx
            #::pmx-model{:model-path "assets/models/SilverWolf/SilverWolf.pmx"}
            #::shader{:use ::pmx-renderer/pmx-shader}
            t3d/default)
      (esse ::bikkuri
            #::assimp{:model-to-load ["assets/models/fx/model_bikkuri.glb"] :config {:tex-unit-offset 1}}
            #::shader{:use ::wirecube/simpleshader}
            pose/default
            #::gltf-renderer{:custom-draw-fn particle/draw-fn}
            t3d/default)))

(defn after-load-fn [world _game]
  (-> world
      (pacing/insert-timeline
       ;; hmmm this API is baaad, need more hammock, artifact first, construct later
       ::silverwolf-pmx
       [[0.0 [[::silverwolf-pmx ::morph/active {"笑い1" 0.0 "にこり" 0.0 "にやり3" 0.0}]]]
        [0.5 [[::bikkuri ::particle/fire {:age-in-step 10}]
              [::silverwolf-pmx ::morph/active {"笑い1" 0.0 "にこり" 0.0 "にやり3" 0.0}]]]])
      (pacing/set-config {:max-progress (* Math/PI 4.0)})
      (esse ::bikkuri #::t3d{:translation (v/vec3 0.5 17.0 3.0)
                             :rotation
                             ;; I kinda want to have quaternion intuition
                             (q/quat-from-axis-angle (v/vec3 0.0 -1.0 0.6) (m/radians 220.0))
                             #_(m/* (q/quat-from-axis-angle (v/vec3 1.0 0.0 0.0) (m/radians 90.0))
                                    (q/quat-from-axis-angle (v/vec3 0.0 0.0 1.0) (m/radians 180.0)))
                             :scale (v/vec3 3.0 0.5 3.0)})
      (esse ::silverwolf-pmx
            #_(pose/strike hand-counting)
            (pose/anime
             [[0.0 (hand-counting {:angka 0 :factor 10.0}) identity]
              [0.4 (hand-counting {:angka 0 :factor 0.0}) easings/ease-out-expo]
              [0.6 (hand-counting {:angka 1 :factor 10.0}) easings/ease-out-expo]
              [0.4 (hand-counting {:angka 1 :factor 0.0}) easings/ease-out-expo]
              [0.6 (hand-counting {:angka 2 :factor 10.0}) easings/ease-out-expo]
              [0.4 (hand-counting {:angka 2 :factor 0.0}) easings/ease-out-expo]
              [0.6 (hand-counting {:angka 3 :factor 10.0}) easings/ease-out-expo]
              [0.4 (hand-counting {:angka 3 :factor 0.0}) easings/ease-out-expo]
              [0.6 (hand-counting {:angka 4 :factor 10.0}) easings/ease-out-expo]
              [0.4 (hand-counting {:angka 4 :factor 0.0}) easings/ease-out-expo]
              [0.6 (hand-counting {:angka 5 :factor 10.0}) easings/ease-out-expo]
              [1.5 (hand-counting {:angka 5 :factor 10.0}) easings/ease-out-expo]
              [1.0 (hand-counting {:angka 0 :factor -15.0}) easings/ease-out-expo]
              [0.5 (hand-counting {:angka 0 :factor 10.0}) easings/ease-out-expo]]
             {:relative? true})
            #::t3d{:translation (v/vec3 0.0 0.0 0.0)
                   :rotation (q/quat-from-axis-angle (v/vec3 0.0 1.0 0.0) (m/radians 0.0))})))

(def rules
  (o/ruleset
   {::secondary-anime
    [:what
     [::world/global ::pacing/progress progress]
     [::time/now ::time/slice 3 {:then false}]
     [::silverwolf-pmx ::pose/pose-tree pose-tree {:then false}]
     :then
     (let [pose-tree (into []
                           (comp
                            (map (fn [{:keys [name translation original-t] :as node}]
                                   (if-let [bone-pose (get {"腰" 0.01} name)]
                                     (let [ampl    bone-pose
                                           tfactor          (* ampl (Math/cos progress))
                                           next-translation (v/vec3 tfactor 0.0 (/ tfactor 3.0))]
                                       (cond-> node
                                         (nil? original-t) (assoc :original-t translation)
                                         next-translation (assoc :translation (m/+ (or original-t translation) next-translation))))
                                     node)))
                            (map (fn [{:keys [name rotation original-r] :as node}]
                                   (if-let [bone-pose (get {"HairLA1_JNT" [(v/vec3 0.3 1.0 0.0) 200.0 2.0]
                                                            "HairLB1_JNT" [(v/vec3 0.3 1.0 0.0) -200.0 3.0]} name)]

                                     (let [[axis-angle offset ampl] bone-pose
                                           next-rotation (q/quat-from-axis-angle
                                                          axis-angle
                                                          (m/radians (* ampl 2.0 (Math/sin (+ (* 2.0 progress) (/ offset 640.0))))))]
                                       (cond-> node
                                         (nil? original-r) (assoc :original-r rotation)
                                         next-rotation (assoc :rotation (m/* (or  original-r rotation) next-rotation))))
                                     node))))
                           pose-tree)]
       (s-> session
            (o/insert ::silverwolf-pmx ::pose/pose-tree pose-tree)))]}))

(def system
  {::world/init-fn #'init-fn
   ::world/after-load-fn #'after-load-fn
   ::world/rules #'rules})
