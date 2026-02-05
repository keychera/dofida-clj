(ns minusthree.engine.rendering
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [engine.math :as m-ext]
   [engine.sugar :refer [vec->f32-arr]]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.world :as world]
   [minusthree.gl.gl-magic :as gl-magic]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_TRIANGLES]]
   [odoyle.rules :as o]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.vector :as v]))

(def project
  (let [[w h]   [540 540]
        fov     45.0
        aspect  (/ w h)]
    (mat/perspective fov aspect 0.1 1000)))

(def view
  (let [position       (v/vec3 0.0 1.0 18.0)
        look-at-target (v/vec3 0.0 0.0 -1.0)
        up             (v/vec3 0.0 1.0 0.0)]
    (mat/look-at position look-at-target up)))

(def model (m-ext/vec3->scaling-mat (v/vec3 0.5)))

(defn rendering-zone [game]
  (let [world   (::world/this game)
        renders (o/query-all world :minusthree.stage.sankyuu/render-data)]
    (doseq [{:keys [program-info gl-data primitives]} renders]
      (let [ctx  nil
            vaos (::gl-magic/vao gl-data)]
        (gl ctx useProgram (:program program-info))
        (cljgl/set-uniform ctx program-info :u_projection (vec->f32-arr (vec project)))
        (cljgl/set-uniform ctx program-info :u_view (vec->f32-arr (vec view)))
        (cljgl/set-uniform ctx program-info :u_model (vec->f32-arr (vec model)))

        (doseq [{:keys [indices vao-name]} primitives]
          (let [vert-count     (:count indices)
                component-type (:componentType indices)
                vao            (get vaos vao-name)]
            (when vao
              (gl ctx bindVertexArray vao)
              (gl ctx drawElements GL_TRIANGLES vert-count component-type 0)
              (gl ctx bindVertexArray #?(:clj 0 :cljs nil))))))))
  game)
