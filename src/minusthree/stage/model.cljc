(ns minusthree.stage.model
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.spec.alpha :as s]
   [engine.sugar :refer [vec->f32-arr]]
   [minusthree.engine.world :as world]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.texture :as texture]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_TEXTURE0 GL_TEXTURE_2D GL_TRIANGLES]]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.shader :as shader]
   [minustwo.systems.transform3d :as t3d]
   [odoyle.rules :as o]))

(s/def ::render-type qualified-keyword?)
(s/def ::render-fn fn?)

(declare render-biasa-fn)
(def default-esse
  (merge {::texture/data {}} t3d/default))
(def biasa (merge {::render-type ::biasa} default-esse))

(defn init-fn [world _game]
  (-> world
      (o/insert ::biasa ::render-fn render-biasa-fn)))

(def rules
  (o/ruleset
   {::render-model-biasa
    [:what
     [esse-id ::render-type render-type]
     [render-type ::render-fn render-fn]
     [esse-id ::gl-magic/casted? true]
     [esse-id ::gl-magic/data gl-data]
     [esse-id ::shader/program-info program-info]
     [esse-id ::gltf/primitives primitives]
     [esse-id ::texture/data tex-data]
     [esse-id ::t3d/transform transform]
     :then
     (println esse-id "ready to render!")]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})

(defn render-biasa-fn
  [{:keys [ctx project view]} {:keys [program-info gl-data tex-data primitives transform]}]
  (let [vaos (::gl-magic/vao gl-data)]
    (gl ctx useProgram (:program program-info))
    (cljgl/set-uniform ctx program-info :u_projection project)
    (cljgl/set-uniform ctx program-info :u_view view)
    (cljgl/set-uniform ctx program-info :u_model (vec->f32-arr (vec transform)))

    (doseq [{:keys [indices vao-name tex-name]} primitives]
      (let [vert-count     (:count indices)
            component-type (:componentType indices)
            vao            (get vaos vao-name)
            tex            (get tex-data tex-name)]
        (when vao
          (when-let [{:keys [tex-unit gl-texture]} tex]
            (gl ctx activeTexture (+ GL_TEXTURE0 tex-unit))
            (gl ctx bindTexture GL_TEXTURE_2D gl-texture)
            (cljgl/set-uniform ctx program-info :u_mat_diffuse tex-unit))

          (gl ctx bindVertexArray vao)
          (gl ctx drawElements GL_TRIANGLES vert-count component-type 0)
          (gl ctx bindVertexArray 0))))))

