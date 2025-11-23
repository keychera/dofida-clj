(ns minusone.scene-in-a-spaceship
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [assets.asset :as asset :refer [asset]]
   [assets.texture :as texture]
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [engine.utils :as utils]
   [engine.world :as world]
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.vao :as vao]
   [minusone.rules.projection :as projection]
   [minusone.rules.transform3d :as t3d]
   [minusone.rules.view.firstperson :as firstperson]
   [odoyle.rules :as o]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]))

;; for the umpteenth time, we learn opengl again
;; https://learnopengl.com/Getting-started/Transformations

(def esse-default-facts [t3d/default])
(defn esse [world id & facts]
  (println "inserting" id)
  (o/insert world id (apply utils/deep-merge (concat esse-default-facts facts))))

(def triangle-data
  (f32-arr
   ; pos            uv
   [-0.5 -0.5 -0.5  0.0 0.0
    0.5 -0.5 -0.5  1.0 0.0
    0.5  0.5 -0.5  1.0 1.0
    0.5  0.5 -0.5  1.0 1.0
    -0.5  0.5 -0.5  0.0 1.0
    -0.5 -0.5 -0.5  0.0 0.0
    -0.5 -0.5  0.5  0.0 0.0
    0.5 -0.5  0.5  1.0 0.0
    0.5  0.5  0.5  1.0 1.0
    0.5  0.5  0.5  1.0 1.0
    -0.5  0.5  0.5  0.0 1.0
    -0.5 -0.5  0.5  0.0 0.
    -0.5  0.5  0.5  1.0 0.0
    -0.5  0.5 -0.5  1.0 1.0
    -0.5 -0.5 -0.5  0.0 1.0
    -0.5 -0.5 -0.5  0.0 1.0
    -0.5 -0.5  0.5  0.0 0.0
    -0.5  0.5  0.5  1.0 0.
    0.5  0.5  0.5  1.0 0.0
    0.5  0.5 -0.5  1.0 1.0
    0.5 -0.5 -0.5  0.0 1.0
    0.5 -0.5 -0.5  0.0 1.0
    0.5 -0.5  0.5  0.0 0.0
    0.5  0.5  0.5  1.0 0.0
    -0.5 -0.5 -0.5  0.0 1.0
    0.5 -0.5 -0.5  1.0 1.0
    0.5 -0.5  0.5  1.0 0.0
    0.5 -0.5  0.5  1.0 0.0
    -0.5 -0.5  0.5  0.0 0.0
    -0.5 -0.5 -0.5  0.0 1.
    -0.5  0.5 -0.5  0.0 1.0
    0.5  0.5 -0.5  1.0 1.0
    0.5  0.5  0.5  1.0 0.0
    0.5  0.5  0.5  1.0 0.0
    -0.5  0.5  0.5  0.0 0.0
    -0.5  0.5 -0.5  0.0 1.0]))

(def vertex-shader
  {:precision  "mediump float"
   :inputs     '{a_pos vec3
                 a_uv vec2}
   :outputs    '{uv vec2}
   :uniforms   '{mvp mat4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position (* mvp (vec4 a_pos "1.0")))
           (= uv a_uv))}})

(def fragment-shader
  {:precision  "mediump float"
   :inputs     '{uv vec2}
   :outputs    '{o_color vec4}
   :uniforms   '{u_tex sampler2D}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= o_color (+ (texture u_tex uv) (vec4 uv "0.5" "0.9"))))}})

(defn init-fn [world game]
  (gl game enable (gl game DEPTH_TEST)) ;; probably better to be called elsewhere
  (-> world
      (asset ::dofida-texture
             #::asset{:type ::asset/texture-from-png :asset-to-load "dofida.png"}
             #::texture{:tex-unit 0})
      (esse ::a-triangle
            #::shader{:program-data (shader/create-program game vertex-shader fragment-shader)}
            #::vao{:entries [{:data triangle-data :buffer-type (gl game ARRAY_BUFFER)}
                             {:attr 'a_pos   :size 3 :type (gl game FLOAT) :stride 20}
                             {:attr 'a_uv    :size 2 :type (gl game FLOAT) :offset 12 :stride 20}]})))

(defn after-load-fn [world _game]
  (-> world
      (firstperson/insert-player (v/vec3 0.0 0.0 3.0) (v/vec3 0.0 0.0 -1.0))
      (esse ::a-cube
            #::t3d{:position (v/vec3 0.0 5.0 0.0)
                   :rotation (q/quat-from-axis-angle
                              (v/vec3 0.0 1.0 0.0)
                              (m/degrees 45.0))})
      (esse ::dofida
            #::t3d{:position (v/vec3 -5.0 0.0 5.0)})))

(def rules
  (o/ruleset
   {::esses
    [:what
     [esse-id ::shader/program-data program-data]
     [esse-id ::vao/vao vao]
     [::dofida-texture ::texture/from-png tex-data]
     [::world/global ::projection/matrix projection]
     [::firstperson/player ::firstperson/look-at look-at {:then false}]
     :then
     (println esse-id "ready to render")]}))

(defn render-fn [world game]
  #_{:clj-kondo/ignore [:inline-def]} ;; debugging purposes
  (def hmm {:world world})
  (doseq [esse (o/query-all world ::esses)]
    (let [{:keys [program-data vao]} esse
          {:keys [tex-unit texture]} (:tex-data esse)
          mvp-loc   (get (:uni-locs program-data) 'mvp)
          u_tex-loc (get (:uni-locs program-data) 'u_tex)
          scale-mat (m-ext/scaling-mat 1.0 1.0 1.0)
          angle     (* (:total-time game) (m/radians -55.0) 0.001)
          
          view      (:look-at esse)
          project   (:projection esse)
          p*v       (m/* project view)]

      (gl game useProgram (:program program-data))
      (gl game bindVertexArray vao)
      (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
      (gl game bindTexture (gl game TEXTURE_2D) texture)
      (gl game uniform1i u_tex-loc tex-unit)

      (doseq [translate [[0.0  0.0  0.0]
                         [2.0  5.0 -15.0]
                         [-1.5 -2.2 -2.5]
                         [-3.8 -2.0 -12.3]
                         [2.4 -0.4 -3.5]
                         [-1.7  3.0 -7.5]
                         [1.3 -2.0 -2.5]
                         [1.5  2.0 -2.5]
                         [1.5  0.2 -1.5]
                         [-1.3  1.0 -1.5]]]
        (let [trans-mat (apply m-ext/translation-mat translate)
              rot-mat   (g/as-matrix (q/quat-from-axis-angle
                                      (v/vec3 (second translate) 1.0 0.0)
                                      (* angle (+ (second translate) 1.0))))
              model     (reduce m/* [trans-mat rot-mat scale-mat])
              p*v*m     (m/* p*v model)]
          (gl game uniformMatrix4fv mvp-loc false (f32-arr (vec p*v*m)))
          (gl game drawArrays (gl game TRIANGLES) 0 36))))))

(def system
  [shader/system
   vao/system
   projection/system
   firstperson/system
   {::world/init-fn init-fn
   ::world/after-load-fn after-load-fn
   ::world/rules rules
   ::world/render-fn render-fn}])

(comment
  (o/query-all (:world hmm))

  :-)
