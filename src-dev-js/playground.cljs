(ns playground
  (:require
   [clojure.spec.test.alpha :as st]
   [clojure.walk :as walk]
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [engine.world :as world]
   [minusone.esse :refer [esse]]
   [minusone.rubahperak :as rubahperak]
   [minusone.rules.gizmo.perspective-grid :as perspective-grid]
   [minusone.rules.gl.vao :as vao]
   [minusone.rules.view.firstperson :as firstperson]
   [minustwo.engine :as engine]
   [minustwo.game :as game]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_ARRAY_BUFFER GL_COLOR_BUFFER_BIT
                                  GL_DEPTH_BUFFER_BIT GL_DEPTH_TEST
                                  GL_ELEMENT_ARRAY_BUFFER GL_FLOAT
                                  GL_ONE_MINUS_SRC_ALPHA GL_SRC_ALPHA
                                  GL_TEXTURE0 GL_TEXTURE_2D GL_TRIANGLES
                                  GL_UNSIGNED_INT]]
   [minustwo.gl.gl-magic :as gl-magic]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.gl.gltf :as gltf]
   [minustwo.gl.macros :refer [webgl] :rename {webgl gl}]
   [minustwo.gl.shader :as shader]
   [minustwo.gl.texture :as texture]
   [minustwo.model.assimp :as assimp]
   [minustwo.systems :as systems]
   [minustwo.systems.view.projection :as projection]
   [minustwo.systems.window :as window]
   [minustwo.utils :as utils]
   [odoyle.rules :as o]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

(st/instrument)

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
   :inputs     '{POSITION vec3}
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
   (fn [world game]
     (println "adhoc system running!")
     (let [ctx (:webgl-context game)]
       (-> world
           (firstperson/insert-player (v/vec3 0.0 18.0 72.0) (v/vec3 0.0 0.0 -1.0))
           (esse ::grid
                 #::shader{:program-info (cljgl/create-program-info ctx perspective-grid/vert perspective-grid/frag)
                           :use ::grid}
                 #::gl-magic{:spell [{:bind-vao ::grid}
                                     {:buffer-data perspective-grid/quad :buffer-type GL_ARRAY_BUFFER}
                                     {:point-attr 'a_pos :use-shader ::grid :count (gltf/gltf-type->num-of-component "VEC2") :component-type GL_FLOAT}
                                     {:buffer-data perspective-grid/quad-indices :buffer-type GL_ELEMENT_ARRAY_BUFFER}
                                     {:unbind-vao true}]})
           (esse ::simpleanime
                 #::assimp{:model-to-load ["assets/simpleanime.gltf"] :tex-unit-offset 0}
                 #::shader{:program-info (cljgl/create-program-info ctx vert frag)
                           :use ::simpleanime})
           (esse ::pmx-shader #::shader{:program-info (cljgl/create-program-info ctx rubahperak/pmx-vert rubahperak/pmx-frag)})
           (esse ::rubahperak
                 #::assimp{:model-to-load ["assets/models/SilverWolf/银狼.pmx"] :tex-unit-offset 1}
                 #::shader{:use ::pmx-shader}))))

   ::world/rules
   (o/ruleset
    {::room-data
     [:what
      [::world/global ::window/dimension window]
      [::world/global ::gl-system/context ctx]
      [::world/global ::projection/matrix project]
      [::firstperson/player ::firstperson/look-at player-view]
      [::firstperson/player ::firstperson/position player-pos]]

     ::grid-model
     [:what
      [::grid ::gl-magic/casted? true]
      [::grid ::shader/program-info grid-prog]]

     ::gltf-models
     [:what
      [esse-id ::gl-magic/casted? true]
      [esse-id ::shader/use shader-id]
      [shader-id ::shader/program-info gltf-prog]
      [esse-id ::gltf/primitives gltf-primitives]]})

   ::world/render-fn
   (fn [world game]
     (let [room-data (utils/query-one world ::room-data)
           ctx       (:ctx room-data)
           project   (:project room-data)
           view      (:player-view room-data)
           view-pos  (:player-pos room-data)
           width     (-> room-data :window :width)
           height     (-> room-data :window :height)]
       (doto ctx
         (gl blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
         (gl clearColor 0.02 0.02 0.12 1.0)
         (gl clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
         (gl viewport 0 0 width height))
       (when-let [render-data (utils/query-one world ::grid-model)]
         (let [inv-proj   (m/invert project)
               inv-view   (m/invert view)
               grid-prog (:grid-prog render-data)
               grid-vao  (get @vao/db* ::grid)]
           (doto ctx
             (gl useProgram (:program grid-prog))
             (gl bindVertexArray grid-vao)

             (cljgl/set-uniform grid-prog 'u_inv_view (f32-arr (vec inv-view)))
             (cljgl/set-uniform grid-prog 'u_inv_proj (f32-arr (vec inv-proj)))
             (cljgl/set-uniform grid-prog 'u_cam_pos (f32-arr (into [] view-pos)))

             (gl disable GL_DEPTH_TEST)
             (gl drawElements GL_TRIANGLES 6 GL_UNSIGNED_INT 0)
             (gl enable GL_DEPTH_TEST))))

       (when-let [gltf-models (o/query-all world ::gltf-models)]
         (doseq [gltf-model gltf-models]
           (let [time       (:total-time game)
                 trans-mat  (m-ext/translation-mat 0.0 -5.0 0.0)
                 rot-mat   (g/as-matrix (q/quat-from-axis-angle
                                         (v/vec3 0.0 1.0 0.0)
                                         (m/radians (* 0.18 time))))
                 scale-mat (m-ext/scaling-mat 2.0)
                 model      (reduce m/* [trans-mat rot-mat scale-mat])
                 gltf-prog  (:gltf-prog gltf-model)]
             (doto ctx
               (gl useProgram (:program gltf-prog))
               (cljgl/set-uniform gltf-prog 'u_projection (f32-arr (vec project)))
               (cljgl/set-uniform gltf-prog 'u_view (f32-arr (vec view)))
               (cljgl/set-uniform gltf-prog 'u_model (f32-arr (vec model))))

             (doseq [prim (:gltf-primitives gltf-model)]
               (let [{:keys [vao-name tex-name indices]} prim
                     count                               (:count indices)
                     component-type                      (:componentType indices)
                     vao                                 (get @vao/db* vao-name)
                     tex                                 (get @texture/db* tex-name)]
                 (when vao
                   (gl ctx bindVertexArray vao)

                   (when-let [{:keys [tex-unit texture]} tex]
                     (gl ctx activeTexture (+ GL_TEXTURE0 tex-unit))
                     (gl ctx bindTexture GL_TEXTURE_2D texture)
                     (cljgl/set-uniform ctx gltf-prog 'u_mat_diffuse tex-unit))

                   (gl ctx drawElements GL_TRIANGLES count component-type 0)
                   (gl ctx bindVertexArray nil)))))))))})

(defonce canvas (js/document.querySelector "canvas"))
(defonce ctx (.getContext canvas "webgl2" (clj->js {:premultipliedAlpha false})))
(defonce width (-> canvas .-clientWidth))
(defonce height (-> canvas .-clientHeight))

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
           #_{:clj-kondo/ignore [:inline-def]}
           (def hmm game)
           (limited-game-loop
            (fn [{:keys [total delta]}]
              (engine/tick (assoc game
                                  :total-time total
                                  :delta-time delta)))
            (fn []
              (println "done!"))
            5000)))))

  (o/query-all @(::world/atom* hmm) ::texture/uri-to-load)

  (let [{:keys [gltf-data bin]} (-> @gltf/debug-data* ::rubahperak)
        gltf-spell (gltf/gltf-spell gltf-data bin {:model-id :hmm
                                                   :use-shader :hmm
                                                   :tex-unit-offset 0})]
    (->> (walk/postwalk (fn [x] (if (instance? js/Uint8Array x) ['uint-arr (.-length x)] x)) gltf-spell)
         (drop 310)))

  :-)
