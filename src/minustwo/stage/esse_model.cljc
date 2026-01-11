(ns minustwo.stage.esse-model
  (:require
   [clojure.spec.alpha :as s]
   [engine.utils :as utils]
   [engine.world :as world]
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
;;       shape: (fn [room model])
;;  3. mat-render-fn to, fn that prep and call render for each materials
;;       shape: (fn [room model material])
;;  4. the materials data

;; we tried this but soon to discover the reactivity problem. render is fine, animation updates not received
;; it's hammock time.

(def rules
  (o/ruleset
   {::esse-model-to-render
    [:what
     [esse-id ::data model]
     [esse-id ::gl-context-fn gl-context-fn]
     [esse-id ::mat-render-fn mat-render-fn]
     [esse-id ::materials materials]]}))

(defn render-fn [world _game]
  (let [room (utils/query-one world ::room/data)]
    (doseq [{:keys [model gl-context-fn mat-render-fn materials]}
            (o/query-all world ::esse-model-to-render)]
      (gl-context-fn room model)
      (doseq [material materials]
        (mat-render-fn room model material)))))

(def system
  {::world/rules #'rules
   ::world/render-fn #'render-fn})

;; keep questioning if what you are doing is worth it
#_"what is worth even? what is it that you are doing? what is question?"
