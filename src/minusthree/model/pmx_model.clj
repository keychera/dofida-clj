(ns minusthree.model.pmx-model
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [vars->map]]
   [engine.sugar :refer [f32-arr i32-arr]]
   [fastmath.matrix :as mat]
   [fastmath.quaternion :as q]
   [fastmath.vector :as v]
   [minusthree.engine.math :refer [translation-mat]]
   [minusthree.engine.utils :refer [get-parent-path]]
   [minusthree.gl.geom :as geom]
   [minustwo.model.pmx-parser :as pmx-parser]))

;; PMX model
(s/def ::bones ::geom/transform-tree)
(s/def ::data (s/keys :req-un [::bones]))

(declare load-pmx-model)

(defn load-pmx-fn [esse-id model-path]
  (fn []
    (let [pmx-data (load-pmx-model model-path)]
      [[esse-id ::data pmx-data]])))

(defn reduce-to-WEIGHTS-and-JOINTS
  "assuming all bones weights are :BDEF1, :BDEF2, or :BDEF4"
  [len]
  (let [WEIGHTS (f32-arr (* 4 len))
        JOINTS  (i32-arr (* 4 len))]
    (fn
      ([] 0)
      ([_counter] (vars->map WEIGHTS JOINTS))
      ([counter weight]
       (let [i (* counter 4)
             b1 (or (:bone-index1 weight) 0)
             b2 (or (:bone-index2 weight) 0)
             b3 (or (:bone-index3 weight) 0)
             b4 (or (:bone-index4 weight) 0)]
         (case (:weight-type weight)
           :BDEF1 (aset WEIGHTS i 1.0)
           :BDEF2
           (let [w1 (:weight1 weight) w2 (- 1.0 w1)]
             (doto WEIGHTS (aset i w1) (aset (+ 1 i) w2)))
           :BDEF4
           (let [w1 (:weight1 weight) w2 (:weight2 weight) w3 (:weight3 weight) w4 (:weight4 weight)]
             (doto WEIGHTS (aset i w1) (aset (+ 1 i) w2) (aset (+ 2 i) w3) (aset (+ 3 i) w4)))
           :noop)
         (doto JOINTS (aset i b1) (aset (+ 1 i) b2) (aset (+ 2 i) b3) (aset (+ 3 i) b4)))
       (inc counter)))))

(defn accumulate-face-count [rf]
  (let [acc (volatile! 0)]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (let [input (assoc input :face-offset @acc)]
         (vswap! acc + (:face-count input))
         (rf result input))))))

(defn pmx-coord->opengl-coord [[x y z]] [x y (- z)])

(defn ccw-face [faces]
  (into [] (comp (partition-all 3) (map (fn [[i0 i1 i2]] [i0 i2 i1])) cat) faces))

(defn resolve-pmx-bones
  "current understanding: pmx bones only have global translation each bones
   this fn will resolve the value of local translation, rotation, invert bind matrix, etc.
   local transform need to be calculated from trans and rot (no scale for now).
   
   assumes bone have :idx denoting its own position in the vector"
  [rf]
  (let [parents-pos! (volatile! {})]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result {:keys [idx parent-bone-idx position] :as bone}]
       (let [position     (pmx-coord->opengl-coord position)
             parent       (get @parents-pos! parent-bone-idx)
             local-pos    (if parent
                            (apply v/vec3 (mapv - position (:position parent)))
                            (v/vec3))
             global-trans (translation-mat position)
             inv-bind-mat (mat/inverse global-trans)
             updated-bone (-> bone
                              (update-keys (fn [k] (or ({:local-name :name} k) k)))
                              (dissoc :position)
                              (assoc :translation      local-pos
                                     :rotation         q/ONE
                                     :inv-bind-mat     inv-bind-mat
                                     :global-transform global-trans
                                     :parent-transform (:transform parent)))]
         (vswap! parents-pos! assoc idx {:position  position
                                         :transform global-trans})
         (rf result updated-bone))))))

(defn load-pmx-model [model-path]
  (let [res-path  (str "public/" model-path)
        pmx-data  (time (pmx-parser/parse-pmx res-path))
        vertices  (:vertices pmx-data)
        POSITION  (float-array (into [] (comp (map :position) (map pmx-coord->opengl-coord) cat) vertices))
        NORMAL    (float-array (into [] (comp (map :normal) (map pmx-coord->opengl-coord) cat) vertices))
        TEXCOORD  (float-array (into [] (mapcat :uv) vertices))
        vert-wj   (transduce (map :weight)
                             (reduce-to-WEIGHTS-and-JOINTS (count vertices))
                             vertices)
        WEIGHTS   (:WEIGHTS vert-wj)
        JOINTS    (:JOINTS vert-wj)
        INDICES   (int-array (ccw-face (:faces pmx-data)))
        parent    (get-parent-path model-path)
        textures  (into [] (map #(str parent "/" %)) (:textures pmx-data))
        materials (into [] accumulate-face-count (:materials pmx-data))
        bones     (into []
                        (comp (map-indexed (fn [idx bone] (assoc bone :idx idx)))
                              resolve-pmx-bones)
                        (:bones pmx-data))
        morphs    (:morphs pmx-data)]
    (vars->map POSITION NORMAL TEXCOORD WEIGHTS JOINTS INDICES
               textures materials bones morphs)))

(comment

  (def miku-pmx (load-pmx-model "assets/models/HatsuneMiku/Hatsune Miku.pmx")))
