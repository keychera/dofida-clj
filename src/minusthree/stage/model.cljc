(ns minusthree.stage.model
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.world :as world]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.texture :as texture]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.systems.transform3d :as t3d]
   [odoyle.rules :as o]))

(s/def ::type #{::biasa})

(def default-esse
  (merge {::texture/data {}}
         t3d/default))

(defn push [world model-type esse-id & facts]
  (o/insert world esse-id (merge {::type model-type} default-esse (apply merge facts))))

(def rules
  (o/ruleset
   {::render-model-biasa
    [:what
     [esse-id ::type ::biasa]
     [esse-id ::gl-magic/casted? true]
     [esse-id ::gl-magic/data gl-data]
     [esse-id ::shader/program-info program-info]
     [esse-id ::gltf/primitives primitives]
     [esse-id ::texture/data tex-data]
     [esse-id ::t3d/transform transform]
     :then
     (println esse-id "ready to render!")]}))

(def system
  {::world/rules #'rules})
