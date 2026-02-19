(ns minusthree.model.model-rendering
  (:require
   #?(:clj  [minusthree.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minusthree.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.spec.alpha :as s]
   [minusthree.anime.anime :as anime]
   [minusthree.engine.transform3d :as t3d]
   [minusthree.engine.world :as world :refer [esse]]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.texture :as texture]
   [minusthree.stage.shaderdef :as shaderdef]
   [minusthree.gl.constants :refer [GL_DYNAMIC_DRAW GL_UNIFORM_BUFFER]]
   [minusthree.gl.shader :as shader]
   [odoyle.rules :as o]))

(s/def ::render-type qualified-keyword?)
(s/def ::render-fn fn?)
(s/def ::ubo some?)

(def default-esse
  (merge {::texture/data {}}
         t3d/default))

(defn create-ubo [ctx size to-index]
  (let [ubo (gl ctx genBuffers)]
    (gl ctx bindBuffer GL_UNIFORM_BUFFER ubo)
    (gl ctx bufferData GL_UNIFORM_BUFFER size GL_DYNAMIC_DRAW)
    (gl ctx bindBufferBase GL_UNIFORM_BUFFER to-index ubo)
    (gl ctx bindBuffer GL_UNIFORM_BUFFER #?(:clj 0 :cljs nil))
    ubo))

(defn init-fn [world _game]
  (-> world
      (esse ::skinning-ubo {::ubo (create-ubo nil (* shaderdef/MAX_JOINTS 16 4) 0)})))

(def rules
  (o/ruleset
   {::render-model-biasa
    [:what
     [esse-id ::render-type render-type]
     [render-type ::render-fn render-fn]
     [esse-id ::shader/program-info program-info]
     [esse-id ::gl-magic/casted? true]
     [esse-id ::gl-magic/data gl-data]
     [esse-id ::texture/data tex-data]
     [esse-id ::texture/count tex-count]
     [esse-id ::anime/pose pose-tree {:then false}]
     ;; need hammock on how to manage ubo
     [::skinning-ubo ::ubo skinning-ubo]
     [esse-id ::t3d/transform transform]
     :when (= tex-count (count tex-data))
     :then (println esse-id "ready to render!")]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})
