(ns minusthree.model.model-rendering
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [fastmath.matrix :refer [mat->float-array]]
   [minusthree.anime.anime :as anime]
   [minusthree.anime.bones :as bones]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.transform3d :as t3d]
   [minusthree.engine.world :as world :refer [esse]]
   [minusthree.gl.cljgl :as cljgl]
   [minusthree.gl.gl-magic :as gl-magic]
   [minusthree.gl.texture :as texture]
   [minusthree.model.gltf-model :as gltf]
   [minusthree.stage.shaderdef :as shaderdef]
   [minustwo.gl.constants :refer [GL_DYNAMIC_DRAW GL_TEXTURE0 GL_TEXTURE_2D
                                  GL_TRIANGLES GL_UNIFORM_BUFFER]]
   [minustwo.gl.shader :as shader]
   [odoyle.rules :as o]))

(s/def ::render-type qualified-keyword?)
(s/def ::render-fn fn?)
(s/def ::ubo some?)

(declare render-gltf)

(def default-esse
  (merge {::texture/data {}}
         t3d/default))

(def gltf (merge {::render-type ::gltf-model} default-esse))

(defn create-ubo [ctx size to-index]
  (let [ubo (gl ctx genBuffers)]
    (gl ctx bindBuffer GL_UNIFORM_BUFFER ubo)
    (gl ctx bufferData GL_UNIFORM_BUFFER size GL_DYNAMIC_DRAW)
    (gl ctx bindBufferBase GL_UNIFORM_BUFFER to-index ubo)
    (gl ctx bindBuffer GL_UNIFORM_BUFFER #?(:clj 0 :cljs nil))
    ubo))

(defn init-fn [world _game]
  (-> world
      (esse ::skinning-ubo {::ubo (create-ubo nil (* shaderdef/MAX_JOINTS 16 4) 0)})
      (o/insert ::gltf-model ::render-fn render-gltf)))

(def rules
  (o/ruleset
   {::load-gltf
    [:what
     [esse-id ::gltf/data gltf-data]
     [esse-id ::gltf/bins bins]
     [esse-id ::loading/state :success]
     [esse-id ::shader/program-info program-info]
     :then
     (let [ctx          nil ; for now, since jvm doesn't need it
           gltf-chant   (gltf/gltf-spell gltf-data (first bins) {:model-id esse-id :use-shader program-info})
           summons      (gl-magic/cast-spell ctx esse-id gltf-chant)
           gl-facts     (::gl-magic/facts summons)
           gl-data      (::gl-magic/data summons)]
       (println esse-id "is loaded!")
       (s-> (reduce o/insert session gl-facts)
            (o/insert esse-id {::gl-magic/casted? true
                               ::gl-magic/data gl-data})))]

    ::render-model-biasa
    [:what
     [esse-id ::render-type render-type]
     [render-type ::render-fn render-fn]
     [esse-id ::shader/program-info program-info]
     [esse-id ::gl-magic/casted? true]
     [esse-id ::gl-magic/data gl-data]
     [esse-id ::texture/data tex-data]
     [esse-id ::texture/count tex-count]
     [esse-id ::gltf/primitives primitives]
     [esse-id ::anime/pose pose-tree {:then false}]
     ;; need hammock on how to manage ubo
     [::skinning-ubo ::ubo skinning-ubo]
     [esse-id ::t3d/transform transform]
     :when (= tex-count (count tex-data))
     :then (println esse-id "ready to render!")]}))

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})

(defn render-gltf
  [{:keys [ctx project view]}
   {:keys [program-info gl-data tex-data primitives transform pose-tree skinning-ubo]}]
  (let [vaos (::gl-magic/vao gl-data)]
    (gl ctx useProgram (:program program-info))
    (cljgl/set-uniform ctx program-info :u_projection project)
    (cljgl/set-uniform ctx program-info :u_view view)
    (cljgl/set-uniform ctx program-info :u_model (mat->float-array transform))

    (when (seq pose-tree)
      (let [^floats joint-mats (bones/create-joint-mats-arr pose-tree)]
        (when (> (alength joint-mats) 0)
          (gl ctx bindBuffer GL_UNIFORM_BUFFER skinning-ubo)
          (gl ctx bufferSubData GL_UNIFORM_BUFFER 0 joint-mats)
          (gl ctx bindBuffer GL_UNIFORM_BUFFER #?(:clj 0 :cljs nil)))))

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
