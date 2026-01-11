(ns minustwo.stage.esse-model
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [vars->map]]
   [engine.utils :as utils]
   [engine.world :as world]
   [minustwo.anime.pose :as pose]
   [minustwo.systems.transform3d :as t3d]
   [minustwo.systems.view.room :as room]
   [odoyle.rules :as o]))

(s/def ::gl-context-fn fn?)
(s/def ::mat-render-fn fn?)
(s/def ::material map?)
(s/def ::materials (s/coll-of ::material))
(s/def ::data map?)

;; heuristic for render order control
;; every model will have these functions:
;;  1. the model data from odoyle's match or anything that the user of this ns provided
;;  2. gl-context-fn that prepare shared stuff in that model (shader, vao, etc.)
;;       shape: (fn [room model dynamic-data])
;;  3. mat-render-fn to, fn that prep and call render for each materials
;;       shape: (fn [room model material dynamic-data])
;;  4. the materials data

;; indirection is key

(def rules
  (o/ruleset
   {::esse-model-to-render
    [:what
     [esse-id ::data model]
     [esse-id ::gl-context-fn gl-context-fn]
     [esse-id ::mat-render-fn mat-render-fn]
     [esse-id ::materials materials]
     [esse-id ::t3d/transform transform]
     [esse-id ::pose/pose-tree pose-tree]]}))

(defn render-fn [world _game]
  (let [room (utils/query-one world ::room/data)]
    (doseq [{:keys [model gl-context-fn mat-render-fn materials
                    transform pose-tree]}
            (o/query-all world ::esse-model-to-render)]
      (let [dynamic-data (vars->map transform pose-tree)]
        (gl-context-fn room model dynamic-data)
        (doseq [material materials]
          (mat-render-fn room model material dynamic-data))))))

(def system
  {::world/rules #'rules
   ::world/render-fn #'render-fn})

;; keep questioning if what you are doing is worth it
#_"what is worth even? what is it that you are doing? what is question?"
