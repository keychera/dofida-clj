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

;; heuristic for render order control
;; every model will have these data:

;;  the model data from odoyle's match or anything that the user of this ns provided
(s/def ::data map?)

;;  prep-fn that prepare shared stuff in that model (shader, vao, etc.)
;;     shape: (fn [room model dynamic-data])
(s/def ::prep-fn fn?)

;;  mat-render-fn to, fn that does the render given all model's materials
;;     shape: (fn [room model materials dynamic-data])
(s/def ::mats-render-fn fn?)

;;  the materials data
(s/def ::material map?)
(s/def ::materials (s/coll-of ::material))

;; then we defined a way to let user specify the way render is ordered
;; by defining a renderplay. a word play from screenplay
(s/def ::renderplay (s/coll-of ::renderbit :kind vector?))

;; renderplay is a coll-of renderbit, that can be either:
(s/def ::renderbit
  (s/or
   ;; custom-fn for custom behaviour
   ;;    shape of (fn [world ctx])
   :custom-fn   (s/keys :req-un [::custom-fn])
   ;; :prep-esse that will invoke ::prep-fn of the given esse-id
   :prep-esse   (s/keys :req-un [::prep-esse])
   ;; :render-esse that will invoke ::mat-render-fn of the given esse-id
   :render-esse (s/keys :req-un [::render-esse])))

(s/def ::custom-fn fn?)
(s/def ::prep-esse some?)
(s/def ::render-esse some?)

(def rules
  (o/ruleset
   {::esse-model-to-render
    [:what
     [esse-id ::data model]
     [esse-id ::prep-fn prep-fn]
     [esse-id ::mats-render-fn mats-render-fn]
     [esse-id ::materials materials]
     [esse-id ::t3d/transform transform]
     [esse-id ::pose/pose-tree pose-tree]]

    ::the-renderplay
    [:what [renderplay-id ::renderplay renderplay]
     :then (println "storing" renderplay-id)]}))

(defn render-fn [world _game]
  (let [room        (utils/query-one world ::room/data)
        models      (o/query-all world ::esse-model-to-render)
        renderplays (o/query-all world ::the-renderplay)]
    (doseq [renderplay renderplays]
      (doseq [renderbit (:renderplay renderplay)]
        ;; hmm this is linear search, need hammock on how to layout static and dynamic data
        ;; maybe atoms for dynamic data like db solution we've been using
        (or (when-let [custom-fn (:custom-fn renderbit)]
              (custom-fn)
              :done)
            (when-let [esse-id (:prep-esse renderbit)]
              (let [{:keys [model prep-fn transform pose-tree]}
                    (some #(when (= (:esse-id %) esse-id) %) models)
                    dynamic-data (vars->map transform pose-tree)]
                (prep-fn room model dynamic-data))
              :done)
            (when-let [esse-id (:render-esse renderbit)]
              (let [{:keys [model mats-render-fn materials transform pose-tree]}
                    (some #(when (= (:esse-id %) esse-id) %) models)
                    dynamic-data (vars->map transform pose-tree)]
                (mats-render-fn room model materials dynamic-data))
              :done))))))

(def system
  {::world/rules #'rules
   ::world/render-fn #'render-fn})

;; keep questioning if what you are doing is worth it
#_"what is worth even? what is it that you are doing? what is question?"
