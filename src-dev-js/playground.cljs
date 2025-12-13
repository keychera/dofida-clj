(ns playground
  (:require
   [clojure.spec.test.alpha :as st]
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [engine.world :as world]
   [minusone.esse :refer [esse]]
   [minusone.rules.gizmo.perspective-grid :as perspective-grid]
   [minusone.rules.gl.vao :as vao]
   [minusone.rules.model.assimp-js :as assimp-js]
   [minusone.rules.view.firstperson :as firstperson]
   [minustwo.engine :as engine]
   [minustwo.game :as game]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_COLOR_BUFFER_BIT
                                  GL_DEPTH_BUFFER_BIT GL_DEPTH_TEST
                                  GL_ELEMENT_ARRAY_BUFFER GL_FLOAT
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA
                                  GL_TRIANGLES GL_UNSIGNED_INT]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.macros :refer [webgl] :rename {webgl gl}]
   [minustwo.gl.shader :as shader]
   [minustwo.systems :as systems]
   [minustwo.systems.view.projection :as projection]
   [minustwo.utils :as utils]
   [odoyle.rules :as o]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(st/instrument)

(defonce canvas (js/document.querySelector "canvas"))
(defonce ctx (.getContext canvas "webgl2" (clj->js {:premultipliedAlpha false})))
(defonce width (-> canvas .-clientWidth))
(defonce height (-> canvas .-clientHeight))

(assimp-js/then-load-model
 ["assets/simpleanime.gltf"]
 #_{:clj-kondo/ignore [:inline-def]}
 (fn [{:keys [gltf bins]}]
   (def gltf-data gltf)
   (def result-bin (first bins))))

(defn limited-game-loop
  ([loop-fn end-fn how-long]
   (limited-game-loop loop-fn end-fn {:total (js/performance.now) :delta 0} how-long))
  ([loop-fn end-fn time-data how-long]
   (if (> how-long 0)
     (js/requestAnimationFrame
      (fn [ts]
        (let [delta (- ts (:total time-data))
              time-data (assoc time-data :total ts :delta delta)]
          (loop-fn time-data)
          (limited-game-loop loop-fn end-fn time-data (- how-long delta)))))
     (end-fn))))

(def vert
  {:precision  "mediump float"
   :inputs     '{POSITION   vec3}
   :uniforms   '{u_model      mat4
                 u_view       mat4
                 u_projection mat4}
   :signatures '{main ([] void)}
   :functions  '{main ([]
                       (=vec4 pos (vec4 POSITION "1.0"))
                       (= gl_Position (* u_projection u_view u_model pos)))}})

(def frag
  {:precision  "mediump float"
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions  '{main ([] (= o_color (vec4 "0.2" "0.2" "0.2" "1.0")))}})

(def adhoc-system
  {::world/init-fn
   (fn [world _game]
     (println "adhoc system running!")
     (-> world
         (firstperson/insert-player (v/vec3 0.0 2.0 24.0) (v/vec3 0.0 0.0 -1.0))
         (esse ::grid
               #::shader{:program-info (cljgl/create-program-info ctx perspective-grid/vert perspective-grid/frag)}
               #::gl-magic{:spells [{:bind-vao ::grid}
                                    {:buffer-data perspective-grid/quad :buffer-type GL_ARRAY_BUFFER}
                                    {:point-attr 'a_pos :use-shader ::grid :count (gltf/gltf-type->num-of-component "VEC2") :component-type GL_FLOAT}
                                    {:buffer-data perspective-grid/quad-indices :buffer-type GL_ELEMENT_ARRAY_BUFFER}
                                    {:unbind-vao true}]})
         (esse ::simpleanime
               #::shader{:program-info (cljgl/create-program-info ctx vert frag)}
                   ;; manually see inside gltf, mesh -> primitives -> accessors -> bufferViews
               #::gl-magic{:spells (gltf/gltf-spell
                                    gltf-data result-bin
                                    {:model-id ::simpleanime
                                     :use-shader ::simpleanime
                                     :tex-unit-offset 0})})))

   ::world/rules
   (o/ruleset
    {::render-data
     [:what
      [::grid ::gl-magic/casted? true]
      [::grid ::shader/program-info grid-prog]
      [::simpleanime ::gl-magic/casted? true]
      [::simpleanime ::shader/program-info gltf-prog]
      [::simpleanime ::gltf/primitives gltf-primitives]
      [::world/global ::projection/matrix project]
      [::firstperson/player ::firstperson/look-at player-view]
      [::firstperson/player ::firstperson/position player-pos]]})

   ::world/render-fn
   (fn [world game]
     (when-let [render-data (utils/query-one world ::render-data)]
       (let [project    (:project render-data)
             view       (:player-view render-data)
             view-pos   (:player-pos render-data)

             inv-proj   (m/invert project)
             inv-view   (m/invert view)

             time       (:total-time game)
             trans-mat  (m-ext/translation-mat 0.0 1.0 0.0)
             rot-mat    (g/as-matrix (q/quat-from-axis-angle
                                      (v/vec3 0.0 0.0 1.0)
                                      (m/radians (* time 0.1))))
             scale-mat  (m-ext/scaling-mat 5.0)
             model      (reduce m/* [trans-mat rot-mat scale-mat])]
         (doto ctx
           (gl blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
           (gl clearColor 0.02 0.02 0.12 1.0)
           (gl clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
           (gl viewport 0 0 width height))

         (let [grid-prog (:grid-prog render-data)
               grid-vao  (get @vao/db* ::grid)]
           (doto ctx
             (gl useProgram (:program grid-prog))
             (gl bindVertexArray grid-vao)

             (cljgl/set-uniform grid-prog 'u_inv_view (f32-arr (vec inv-view)))
             (cljgl/set-uniform grid-prog 'u_inv_proj (f32-arr (vec inv-proj)))
             (cljgl/set-uniform grid-prog 'u_cam_pos (f32-arr (into [] view-pos)))

             (gl disable GL_DEPTH_TEST)
             (gl drawElements GL_TRIANGLES 6 GL_UNSIGNED_INT 0)
             (gl enable GL_DEPTH_TEST)))

         (let [gltf-prog (:gltf-prog render-data)]
           (doseq [prim (:gltf-primitives render-data)]
             (let [{:keys [vao-name
                           indices]} prim
                   count             (:count indices)
                   component-type    (:componentType indices)
                   gltf-vao          (get @vao/db* vao-name)]
               (doto ctx
                 (gl useProgram (:program gltf-prog))
                 (gl bindVertexArray gltf-vao)

                 (cljgl/set-uniform gltf-prog 'u_projection (f32-arr (vec project)))
                 (cljgl/set-uniform gltf-prog 'u_view (f32-arr (vec view)))
                 (cljgl/set-uniform gltf-prog 'u_model (f32-arr (vec model)))

                 (gl drawElements GL_TRIANGLES count component-type 0))))))))})

(comment
  (with-redefs
   [systems/all (conj systems/all adhoc-system)]
    (doto ctx
      (gl blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
      (gl clearColor 0.02 0.02 0.12 1.0)
      (gl clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
      (gl viewport 0 0 width height))
    (-> (game/->game {:webgl-context ctx
                      :total-time 0
                      :delta-time 0})
        (engine/init)
        ((fn [game]
           (limited-game-loop
            (fn [{:keys [total delta]}]
              (engine/tick (assoc game
                                  :total-time total
                                  :delta-time delta)))
            (fn []
              (println "done!")
              (println (o/query-all @(::world/atom* game))))
            2000)))))

  (:skins gltf-data)
  (:nodes gltf-data)
  (take 6 (:accessors gltf-data))
  (:bufferViews gltf-data)
  (-> gltf-data :meshes first)
  (:buffers gltf-data)

  :-)
