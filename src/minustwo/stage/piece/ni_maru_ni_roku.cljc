(ns minustwo.stage.piece.ni-maru-ni-roku
  (:require
   [clojure.string :as str]
   [engine.game :refer [gl-ctx]]
   [engine.macros :refer [s->]]
   [engine.math :as m-ext]
   [engine.math.easings :as easings]
   [engine.world :as world :refer [esse]]
   [minustwo.anime.morph :as morph]
   [minustwo.anime.pacing :as pacing]
   [minustwo.anime.pose :as pose]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.shader :as shader]
   [minustwo.model.assimp :as assimp]
   [minustwo.model.pmx-model :as pmx-model]
   [minustwo.stage.gltf-renderer :as gltf-renderer]
   [minustwo.stage.pmx-renderer :as pmx-renderer]
   [minustwo.stage.pseudo.particle :as particle]
   [minustwo.systems.time :as time]
   [minustwo.systems.transform3d :as t3d]
   [odoyle.rules :as o]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]
   [minustwo.systems.view.camera :as camera]
   [minustwo.anime.anime :as anime]
   [thi.ng.geom.core :as g]
   [minustwo.systems.view.firstperson :as firstperson]
   [minustwo.stage.piece.ni-maru-ni-roku :as ni-maru-ni-roku]))

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

(defn hitung [which angka]
  (fn kazoeru-fn [name]
    (let [flipper (case which "左" 1.0 "右" -1.0)
          [matches? finger level] (re-matches (re-pattern (str which "([親人中薬小])指([０１２３])")) name)]
      (when matches?
        (let [;; the finger other than 親 doesn't have local-axis data, manually taken from 左手首
              wrist-x-axis (g/scale (v/vec3 0.771123 -0.635213 0.043293) flipper)
              wrist-z-axis (g/scale (v/vec3 -0.033414 0.027528 0.999062) flipper)
              wrist-y-axis (m/cross wrist-x-axis wrist-z-axis)]
          (if (= "親" finger)
            {:r-fn (rotate-local-fn (if (>= angka 5) {:y -20} {:x (* flipper 70.0) :z (* flipper -10.0)}))}
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

(defn hand-counting [{:keys [which twist wrist armpit angka factor factor2]
                      :or {which "左" twist 0.0 wrist 15.0 armpit -16.0 factor2 0.0}}]
  (let [flipper (case which "左" 1.0 "右" -1.0)]
    (comp
      (do-pose
        (or-fn
          {(str which "手捩") {:r-fn (rotate-local-fn {:x (* flipper twist)})}
           (str which "腕捩") {:r-fn (rotate-local-fn {:z (* flipper twist)})}
           (str which "腕")   {:r-fn (rotate-local-fn {:y (+ 30.0 factor2) :z (* flipper armpit)})}
           (str which "ひじ") {:r-fn (rotate-local-fn {:x (* flipper -100.0)  :z (* flipper (+ -110.0 (* 0.22 factor)))})}
           (str which "手首") {:r-fn (rotate-local-fn {:x (* flipper twist) :y wrist :z (* flipper factor)})}}
          (hitung which angka))))))

(def default-fx-t3d
  #::t3d{:translation (v/vec3 0.5 17.0 3.0)
         :rotation
                ;; I want to have quaternion intuition!
         (q/quat-from-axis-angle (v/vec3 0.0 -1.0 0.6) (m/radians 220.0))
         #_(m/* (q/quat-from-axis-angle (v/vec3 1.0 0.0 0.0) (m/radians 90.0))
             (q/quat-from-axis-angle (v/vec3 0.0 0.0 1.0) (m/radians 180.0)))
         :scale (v/vec3 2.0 0.5 2.0)})

(defn default-fx-esse
  ([world model-name-keyword] (default-fx-esse world model-name-keyword (name model-name-keyword)))
  ([world model-name-keyword model-file]
   (esse world model-name-keyword
     #::assimp{:model-to-load [(str "assets/models/fx/" model-file ".glb")] :config {:tex-unit-offset 1}}
     #::shader{:use ::fx-text-shader}
     pose/default
     #::gltf-renderer{:custom-draw-fn particle/draw-fn}
     default-fx-t3d)))

(def text-fx-vertex-shader
  (str cljgl/version-str
    "
     precision mediump float;

     in vec3 POSITION;
     in vec3 NORMAL;
     in vec2 TEXCOORD_0;
 
     uniform mat4 u_model;
     uniform mat4 u_view;
     uniform mat4 u_projection;
     
     out vec3 Normal;
     out vec2 TexCoord;

     void main() {
        gl_Position = u_projection * u_view * u_model * vec4(POSITION, 1.0);
        Normal = NORMAL;
        TexCoord = TEXCOORD_0;
     }"))

(def text-fx-fragment-shader
  (str cljgl/version-str
    "
     precision mediump float;
     
     in vec3 Normal;
     in vec2 TexCoord;

     out vec4 o_color;

     void main() {
        o_color = vec4(0.56, 0.74, 0.95, 1.0); 
     }
    "))

(defn make-anime [easing-fn]
  (fn pos-anime-fn [t origin-v cur-v next-v]
    (let [t (easing-fn t)
          interpolated (m/+ cur-v (g/scale next-v t))]
      (m/+ origin-v interpolated))))

(defn init-fn [world _game]
  (-> world
    (camera/look-at-target (v/vec3 3.0 20.5 -3.0) (v/vec3 0.5 17.0 3.0) (v/vec3 0.0 1.0 0.0))
    (esse ::silverwolf-pmx
      #::pmx-model{:model-path "assets/models/SilverWolf/SilverWolf.pmx"}
      #::shader{:use ::pmx-renderer/pmx-shader}
      t3d/default)
    (default-fx-esse ::model_bikkuri)
    (default-fx-esse ::model_num0)
    (default-fx-esse ::model_num0-2 "model_num0")
    (default-fx-esse ::model_num1)
    (default-fx-esse ::model_num2)
    (default-fx-esse ::model_num2-2 "model_num2")
    (default-fx-esse ::model_num3)
    (default-fx-esse ::model_num4)
    (default-fx-esse ::model_num5)
    (default-fx-esse ::model_num6)))

(defn after-load-fn [world game]
  (-> world
    (pacing/set-config {:max-progress (* Math/PI 4.0)})
    (esse ::fx-text-shader
      #::shader{:program-info (cljgl/create-program-info-from-source (gl-ctx game) text-fx-vertex-shader text-fx-fragment-shader)})
    #_(camera/look-at-target (v/vec3 3.0 20.5 -3.0) (v/vec3 0.5 17.0 3.0) (v/vec3 0.0 1.0 0.0))
    (camera/look-at-target (v/vec3 0.0 17.0 8.0) (v/vec3 0.0 17.0 3.0) (v/vec3 0.0 1.0 0.0))
    (anime/insert ::world/global
      (let [ident-fn (make-anime identity)]
        {::camera/position
         {:origin-val (v/vec3 3.0 20.5 -3.0)
          :timeline   [[0.0 (v/vec3) ident-fn]
                       [5.0 (v/vec3 0.0 0.0 1.0) ident-fn]
                       [1.0 (v/vec3 -3.0 -3.5 11.0) ident-fn]]}
         ::camera/look-at-target
         {:origin-val (v/vec3 0.5 17.0 3.0)
          :timeline   [[0.0 (v/vec3) ident-fn]
                       [5.0 (v/vec3) ident-fn]
                       [1.0 (v/vec3 -0.5 0.0 0.0) ident-fn]]}}))

    (pacing/insert-timeline
       ;; hmmm this API is baaad, need more hammock, artifact first, construct later
      ::adhoc-facts-timeline
      (let [ni-maru-ni-roku-size #::t3d{:rotation (q/quat-from-axis-angle (v/vec3 1.0 0.0 0.0) (m/radians 90.0)) :scale (v/vec3 1.5 0.5 1.5)}
            slow-particle {::particle/fire {:age-in-step 120 :physics {:initial-velocity (v/vec3 0.0 1e-4 0.0)
                                                                       :gravity (v/vec3 1e-7 -1e-6 1e-6)}}}]
        [[0.0 [[::model_num2 default-fx-t3d]
               [::model_num2-2 default-fx-t3d]
               [::model_num6 default-fx-t3d]
               [::silverwolf-pmx ::morph/active {"笑い1" 0.0 "にこり" 0.0 "にやり3" 0.0}]]]
         [0.5 [[::model_num1 ::particle/fire {:age-in-step 20
                                              :physics {:initial-velocity (v/vec3 1.5e-4 1.5e-3 0.0)}}]]]
         [1.0 [[::model_num2 ::particle/fire {:age-in-step 20
                                              :physics {:initial-velocity (v/vec3 1.5e-4 1.5e-3 0.0)}}]]]
         [1.0 [[::model_num3 ::particle/fire {:age-in-step 20
                                              :physics {:initial-velocity (v/vec3 1.5e-4 1.5e-3 0.0)}}]]]
         [1.0 [[::model_num4 ::particle/fire {:age-in-step 20 :physics {:initial-velocity (v/vec3 1.5e-4 1.5e-3 0.0)}}]]]
         [1.0 [[::model_num5 ::particle/fire {:age-in-step 20 :physics {:initial-velocity (v/vec3 1.5e-4 1.5e-3 0.0)}}]]]
         [2.2 [[::model_num2 (merge #::t3d{:translation (v/vec3 -1.0 16.5 4.0)} ni-maru-ni-roku-size)]
               [::model_num2 slow-particle]
               [::model_num0 (merge #::t3d{:translation (v/vec3 -0.35 16.5 4.0)} ni-maru-ni-roku-size)]
               [::model_num0 slow-particle]
               [::model_num2-2 (merge #::t3d{:translation (v/vec3 0.35 16.5 4.0)} ni-maru-ni-roku-size)]
               [::model_num2-2 slow-particle]
               [::model_num6 (merge #::t3d{:translation (v/vec3 1.0 16.5 4.0)} ni-maru-ni-roku-size)]
               [::model_num6 slow-particle]
               [::silverwolf-pmx ::morph/active {"笑い1" 0.0 "にこり" 0.0 "にやり3" 1.0}]]]]))
    (esse ::silverwolf-pmx
      #_(pose/strike
          (comp
            (hand-counting {:angka 6 :factor 10.0 :twist 60 :wrist -15 :armpit -24})
            (hand-counting {:angka 2 :which "右" :twist 60  :wrist -25 :armpit -24 :factor 20.0})))
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
        ;;  [0.4 (hand-counting {:angka 5 :factor 0.0}) easings/ease-out-expo]
         [1.5 (comp
                (hand-counting {:angka 0 :factor 10.0 :factor2 10.0 :twist 30})
                (hand-counting {:angka 0 :factor 20.0 :factor2 10.0 :twist 30 :which "右"})) easings/ease-out-expo]
         [0.7 (comp
                (hand-counting {:angka 6 :factor 10.0 :twist 60 :wrist -25 :armpit -24})
                (hand-counting {:angka 2 :factor 10.0 :twist 60  :wrist -25 :armpit -24 :which "右"})) easings/ease-out-back]
         [32.0 (comp
                (hand-counting {:angka 6 :factor 10.0 :factor2 5.0 :twist 60 :wrist -25 :armpit -24})
                (hand-counting {:angka 2 :factor 20.0 :factor2 5.0 :twist 60  :wrist -25 :armpit -24 :which "右"})) identity]]
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
                                         next-translation (v/vec3 0.0 tfactor (/ tfactor 3.0))]
                                     (cond-> node
                                       (nil? original-t) (assoc :original-t translation)
                                       next-translation (assoc :translation (m/+ (or original-t translation) next-translation))))
                                   node)))
                          (map (fn [{:keys [name rotation original-r] :as node}]
                                 (if-let [bone-pose (get {"HairLA1_JNT" [(v/vec3 0.3 1.0 0.0) 200.0 2.0]
                                                          "HairLB1_JNT" [(v/vec3 0.3 1.0 0.0) -200.0 3.0]
                                                          "HairRA1_JNT" [(v/vec3 0.3 1.0 0.0) 200.0 2.0]
                                                          "HairRB1_JNT" [(v/vec3 0.3 1.0 0.0) -200.0 3.0]
                                                          "HairFA1_JNT" [(v/vec3 0.3 1.0 0.0) -200.0 3.0]
                                                          "HairFB1_JNT" [(v/vec3 0.3 1.0 0.0) 200.0 3.0]
                                                          "HairFC1_JNT" [(v/vec3 0.3 1.0 0.0) -200.0 3.0]} name)]

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
