(ns rules.test-atlas
  (:require
   [assets.asset :as asset :refer [asset]]
   [assets.atlas :as atlas]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.instances :as instances]
   [play-cljc.math :as m]
   [play-cljc.transforms :as t]))

(def system
  {::world/init-fn
   (fn [world _game]
     (-> world
         (asset ::eye-atlas
                #::asset{:type ::asset/atlas
                         :asset-to-load "atlas/eye.png"
                         :metadata-to-load "atlas/eye.json"})))

   ::world/rules
   (o/ruleset
    {::atlas-loaded?
     [:what
      [::eye-atlas ::asset/loaded? true]
      :then
      (println "atlas eye loaded!")]})

   ::world/render-fn
   (fn [world game]
     (when (seq (o/query-all world ::atlas-loaded?))
       (let [atlas-id   ::eye-atlas
             frame-name "pupil.png"
             asset-db   @(:db* (first (o/query-all world ::asset/db*)))
             atlas      (get asset-db ::eye-atlas)
             atlas-inst (::atlas/instanced (get asset-db atlas-id))
             atlas-raw  (::atlas/raw (get asset-db atlas-id))
             metadata   (::atlas/metadata atlas)
             frame      (->> metadata :frames
                             (filter #(= (:filename %) frame-name))
                             (first))

             [width height] (utils/get-size game)
             project        (m/projection-matrix width height)

             instance-esses
             (into [] (map (fn [frame]
                             (let [frame        (:frame frame)
                                   crop-x       (:x frame)
                                   crop-y       (:y frame)
                                   frame-width  (:w frame)
                                   frame-height (:h frame)
                                   translate    (m/translation-matrix (/ width 3) (/ height 3))
                                   rotate       (m/rotation-matrix 0.0)
                                   sprite-scale (m/scaling-matrix frame-width frame-height)
                                   model-matrix (reduce m/multiply-matrices
                                                        (remove nil? [sprite-scale rotate translate]))
                                   projected    (m/multiply-matrices model-matrix project)]
                               (-> atlas-raw
                                   (update-in [:uniforms 'u_matrix] #(m/multiply-matrices projected %))
                                   (t/crop crop-x crop-y frame-width frame-height)))))
                   [frame])
             instance (reduce-kv instances/assoc atlas-inst instance-esses)]
         (c/render game instance))))})