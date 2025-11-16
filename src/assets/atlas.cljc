(ns assets.atlas
  (:require
   [assets.asset :as asset]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as e2d]
   [play-cljc.instances :as instances]))

(s/def ::instance-loaded? boolean?)
(s/def ::metadata-loaded? boolean?)

(def system
  {::world/rules
   (o/ruleset
    {::subdata-loading-progress
     [:what
      [asset-id ::instance-loaded? true]
      [asset-id ::metadata-loaded? true]
      :then
      (println "atlas loaded for" asset-id)
      (s-> session
           (o/retract asset-id ::instance-loaded?)
           (o/retract asset-id ::metadata-loaded?)
           (o/insert asset-id ::asset/loaded? true))]})})

(defmethod asset/process-asset ::asset/atlas
  [world* game asset-id {::asset/keys [asset-to-load metadata-to-load]}]
  (swap! world*
         #(-> %
              (o/insert asset-id ::instance-loaded? false)
              (o/insert asset-id ::metadata-loaded? false)))
  (utils/get-image
   asset-to-load
   (fn [{:keys [data width height]}]
     (let [raw-entity (e2d/->image-entity game data width height)
           instanced-entity (c/compile game (instances/->instanced-entity raw-entity))
           instanced-entity (assoc instanced-entity :width width :height height)]
       (println "loaded atlas asset from" asset-to-load)
       (swap! (::asset/db* game)
              #(-> %
                   (assoc-in [asset-id ::raw] raw-entity)
                   (assoc-in [asset-id ::instanced] instanced-entity)))
       (swap! world* #(-> % (o/insert asset-id ::instance-loaded? true))))))
  (utils/get-json
   metadata-to-load
   (fn [loaded-metadata]
     (println "metadata loaded" (:meta loaded-metadata))
     (swap! (::asset/db* game) update-in [asset-id ::metadata] merge loaded-metadata)
     (swap! world* #(-> % (o/insert asset-id ::metadata-loaded? true))))))
