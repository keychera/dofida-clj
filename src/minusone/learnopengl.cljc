(ns minusone.learnopengl
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [assets.asset :as asset :refer [asset]]
   [assets.texture :as texture]
   [engine.math :as m-ext]
   [engine.sugar :refer [f32-arr]]
   [engine.utils :as utils]
   [engine.world :as world]
   [minusone.rules.gl.magic :as gl-magic]
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.vao :as vao]
   [minusone.rules.projection :as projection]
   [minusone.rules.transform3d :as t3d]
   [minusone.rules.view.firstperson :as firstperson]
   [odoyle.rules :as o]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.quaternion :as q]
   [thi.ng.geom.vector :as v]
   [thi.ng.math.core :as m]
   [thi.ng.geom.matrix :as mat]))

;; for the umpteenth time, we learn opengl again
;; https://learnopengl.com/Getting-started/Transformations

(def esse-default-facts [t3d/default])
(defn esse [world id & facts]
  (o/insert world id (apply utils/deep-merge (concat esse-default-facts facts))))

(def cube-data
  (f32-arr
   ; pos            normal              uv
   [-0.5 -0.5 -0.5  0.0  0.0 -1.0   0.0 0.0
    0.5 -0.5 -0.5  0.0  0.0 -1.0   1.0 0.0
    0.5  0.5 -0.5  0.0  0.0 -1.0   1.0 1.0
    0.5  0.5 -0.5  0.0  0.0 -1.0   1.0 1.0
    -0.5  0.5 -0.5  0.0  0.0 -1.0    0.0 1.0
    -0.5 -0.5 -0.5  0.0  0.0 -1.0    0.0 0.0

    -0.5 -0.5  0.5  0.0  0.0  1.0    0.0 0.0
    0.5 -0.5  0.5  0.0  0.0  1.0   1.0 0.0
    0.5  0.5  0.5  0.0  0.0  1.0   1.0 1.0
    0.5  0.5  0.5  0.0  0.0  1.0   1.0 1.0
    -0.5  0.5  0.5  0.0  0.0  1.0    0.0 1.0
    -0.5 -0.5  0.5  0.0  0.0  1.0    0.0 0.0

    -0.5  0.5  0.5 -1.0  0.0  0.0    1.0 0.0
    -0.5  0.5 -0.5 -1.0  0.0  0.0    1.0 1.0
    -0.5 -0.5 -0.5 -1.0  0.0  0.0    0.0 1.0
    -0.5 -0.5 -0.5 -1.0  0.0  0.0    0.0 1.0
    -0.5 -0.5  0.5 -1.0  0.0  0.0    0.0 0.0
    -0.5  0.5  0.5 -1.0  0.0  0.0    1.0 0.

    0.5  0.5  0.5  1.0  0.0  0.0   1.0 0.0
    0.5  0.5 -0.5  1.0  0.0  0.0   1.0 1.0
    0.5 -0.5 -0.5  1.0  0.0  0.0   0.0 1.0
    0.5 -0.5 -0.5  1.0  0.0  0.0   0.0 1.0
    0.5 -0.5  0.5  1.0  0.0  0.0   0.0 0.0
    0.5  0.5  0.5  1.0  0.0  0.0   1.0 0.0

    -0.5 -0.5 -0.5  0.0 -1.0  0.0    0.0 1.0
    0.5 -0.5 -0.5  0.0 -1.0  0.0   1.0 1.0
    0.5 -0.5  0.5  0.0 -1.0  0.0   1.0 0.0
    0.5 -0.5  0.5  0.0 -1.0  0.0   1.0 0.0
    -0.5 -0.5  0.5  0.0 -1.0  0.0   0.0 0.0
    -0.5 -0.5 -0.5  0.0 -1.0  0.0   0.0 1.

    -0.5  0.5 -0.5  0.0  1.0  0.0   0.0 1.0
    0.5  0.5 -0.5  0.0  1.0  0.0   1.0 1.0
    0.5  0.5  0.5  0.0  1.0  0.0   1.0 0.0
    0.5  0.5  0.5  0.0  1.0  0.0   1.0 0.0
    -0.5  0.5  0.5  0.0  1.0  0.0   0.0 0.0
    -0.5  0.5 -0.5  0.0  1.0  0.0   0.0 1.0]))

(def vertex-shader
  {:precision  "mediump float"
   :inputs     '{a_pos vec3
                 a_normal vec3
                 a_uv vec2}
   :outputs    '{fragpos vec3
                 normal vec3
                 uv vec2}
   :uniforms   '{u_p_v mat4
                 u_model mat4
                 u_normal_mat mat3}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (=vec4 pos (* u_model (vec4 a_pos "1.0")))
           (= gl_Position (* u_p_v pos))
           (= fragpos (vec3 pos))
           (= normal (* u_normal_mat a_normal))
           (= uv a_uv))}})

(def cube-fs
  {:precision  "mediump float"
   :inputs     '{fragpos vec3
                 normal vec3
                 uv vec2}
   :outputs    '{o_color vec4}
   :uniforms   '{u_view_pos vec3

                 u_mat_diffuse sampler2D
                 u_mat_specular sampler2D
                 u_mat_shininess float

                 u_light_pos vec3
                 u_light_ambient vec3
                 u_light_diffuse vec3
                 u_light_specular vec3}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           ; ambient
           (=vec3 ambient (* u_light_ambient (.rgb (texture u_mat_diffuse uv))))
           
           ; diffuse
           (=vec3 norm (normalize normal))
           (=vec3 light_dir (normalize (- u_light_pos fragpos)))
           (=float diff (max (dot norm light_dir) "0.0"))
           (=vec3 diffuse (* u_light_diffuse diff (.rgb (texture u_mat_diffuse uv))))

           (=vec3 view_dir (normalize (- u_view_pos fragpos)))
           (=vec3 reflect_dir (reflect (- "0.0" light_dir) norm))
           (=float spec (pow (max (dot view_dir reflect_dir) "0.0") u_mat_shininess))
           (=vec3 specular (* u_light_specular spec (.rgb (texture u_mat_specular uv))))
           
           (= o_color (vec4 (+ ambient diffuse specular) "1.0")))}})

(def light-cube-fs
  {:precision  "mediump float"
   :inputs     '{normal vec3
                 uv vec2}
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (= o_color (vec4 "1.0")))}})

(defn init-fn [world game]
  (gl game enable (gl game DEPTH_TEST)) ;; probably better to be called elsewhere
  (-> world
      (asset ::container-texture
             #::asset{:type ::asset/texture-from-png :asset-to-load "from_learnopengl/container.png"}
             #::texture{:tex-unit 0})
      (asset ::specular-map
             #::asset{:type ::asset/texture-from-png :asset-to-load "from_learnopengl/specular_map.png"}
             #::texture{:tex-unit 1})
      (esse ::a-cube
            #::shader{:program-data (shader/create-program game vertex-shader cube-fs)}
            #::vao{:use :cube-vao})
      (esse ::light-cube
            #::shader{:program-data (shader/create-program game vertex-shader light-cube-fs)}
            #::vao{:use :light-cube-vao})
      (esse ::shader/global
            #::gl-magic{:incantation
                        [{:bind-buffer "cube" :buffer-data cube-data :buffer-type (gl game ARRAY_BUFFER)}
                         {:bind-vao :cube-vao}
                         {:point-attr 'a_pos :from-shader ::a-cube :attr-size 3 :attr-type (gl game FLOAT) :stride 32}
                         {:point-attr 'a_normal :from-shader ::a-cube :attr-size 3 :attr-type (gl game FLOAT) :offset 12 :stride 32}
                         {:point-attr 'a_uv :from-shader ::a-cube :attr-size 2 :attr-type (gl game FLOAT) :offset 24 :stride 32}
                         {:bind-vao :light-cube-vao}
                         ;; rebinding actually make the data disappear for light-cube-vao
                         {:point-attr 'a_pos :from-shader ::light-cube :attr-size 3 :attr-type (gl game FLOAT) :stride 32}
                         {:unbind-vao true}]})
      (firstperson/insert-player (v/vec3 0.0 0.0 3.0) (v/vec3 0.0 0.0 -1.0))))

(defn after-load-fn [world _game]
  (-> world
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
     [esse-id ::vao/use vao-id]
     [vao-id ::vao/vao vao]
     [::container-texture ::texture/from-png tex-data]
     [::specular-map ::texture/from-png spec-tex-data]
     [::world/global ::projection/matrix projection]
     [::firstperson/player ::firstperson/look-at look-at {:then false}]
     [::firstperson/player ::firstperson/position cam-position {:then false}]
     :then
     (println esse-id "ready to render")]}))

(defonce random-cubes
  (let [max-dist 10.0
        min-dist 0.1
        rand-val (fn [] (loop []
                          (let [v (* max-dist (- (rand 1) 0.5))]
                            (if (> (abs v) min-dist) v (recur)))))]
    (take 200 (repeatedly (fn [] [(rand-val) (rand-val) (rand-val)])))))

#?(:clj #_"java only profilers"
   (comment
     (require '[clj-async-profiler.core :as prof])

     (prof/start #_{:event :alloc})
     (prof/stop)

     (require '[clj-java-decompiler.core :refer [decompile]])
     (binding [*compiler-options* {:disable-locals-clearing false}]
       (spit ".zzz/This.java"
             (with-out-str
               (decompile (println "hell0")))))

     :-))

(defn render-fn [world game]
  #_{:clj-kondo/ignore [:inline-def]} ;; debugging purposes
  (def hmm {:world world})
  (let [light-pos [0.0 1.0 -2.0]]
    #_"light cube, deliberate code duplication because we haven't senses the common denominator yet"
    (when-let [esse (first (->> (o/query-all world ::esses) (filter #(= (:esse-id %) ::light-cube))))]
      (let [{:keys [program-data vao]} esse
            cube-uni  (:uni-locs program-data)
            ^int u_p_v (get cube-uni 'u_p_v)
            ^int u_model (get cube-uni 'u_model)
            view      (:look-at esse)
            project   (:projection esse)
            p*v       (m/* project view)

            scale-mat (m-ext/scaling-mat 0.2)
            trans-mat (apply m-ext/translation-mat light-pos)
            model     (reduce m/* [trans-mat scale-mat])]
        (gl game useProgram (:program program-data))
        (gl game bindVertexArray vao)
        (gl game uniformMatrix4fv u_p_v false (f32-arr (vec p*v)))
        (gl game uniformMatrix4fv u_model false (f32-arr (vec model)))
        (gl game drawArrays (gl game TRIANGLES) 0 36)))

    (when-let [esse (first (->> (o/query-all world ::esses) (filter #(= (:esse-id %) ::a-cube))))]
      (let [{:keys [program-data vao cam-position]} esse
            cube-uni  (:uni-locs program-data)

            ;; uniform for vertex shaders
            ^int u_p_v (get cube-uni 'u_p_v)
            ^int u_model (get cube-uni 'u_model)
            ^int u_normal_mat (get cube-uni 'u_normal_mat)

            ;; uniform for fragment shaders
            ^int u_view_pos (get cube-uni 'u_view_pos)

            ^int u_mat_diffuse (get cube-uni 'u_mat_diffuse)
            ^int u_mat_specular (get cube-uni 'u_mat_specular)
            ^int u_mat_shininess (get cube-uni 'u_mat_shininess)

            ^int u_light_pos (get cube-uni 'u_light_pos)
            ^int u_light_ambient (get cube-uni 'u_light_ambient)
            ^int u_light_diffuse (get cube-uni 'u_light_diffuse)
            ^int u_light_specular (get cube-uni 'u_light_specular)

            angle     (* (:total-time game) (m/radians -55.0) 0.001)

            view      (:look-at esse)
            project   (:projection esse)
            p*v       (m/* project view)]

        (gl game useProgram (:program program-data))
        (gl game bindVertexArray vao)

        (let [{:keys [tex-unit texture]} (:tex-data esse)]
          (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
          (gl game bindTexture (gl game TEXTURE_2D) texture)
          (gl game uniform1i u_mat_diffuse tex-unit))

        (let [{:keys [tex-unit texture]} (:spec-tex-data esse)]
          (gl game activeTexture (+ (gl game TEXTURE0) tex-unit))
          (gl game bindTexture (gl game TEXTURE_2D) texture)
          (gl game uniform1i u_mat_specular tex-unit))

        (gl game uniform3fv u_view_pos (f32-arr (into [] cam-position))) ;; this is weird... (f32-arr (vec (v/vec3))) returns empty arr
        (gl game uniform3fv u_light_pos (f32-arr light-pos))

        (gl game uniform3fv u_light_ambient (f32-arr [0.2 0.2 0.2]))
        (gl game uniform3fv u_light_diffuse (f32-arr [0.5 0.5 0.5]))
        (gl game uniform3fv u_light_specular (f32-arr [1.0 1.0 1.0]))

        (gl game uniform1f u_mat_shininess 64.0)

        (doseq [translate random-cubes]
          (let [trans-mat  (apply m-ext/translation-mat translate)
                scale-mat (m-ext/scaling-mat 1.0)
                rot-mat    (g/as-matrix (q/quat-from-axis-angle
                                         (v/vec3 (first translate) (second translate) (first translate))
                                         (* angle (+ (second translate) 1.0))))
                model      (reduce m/* [trans-mat rot-mat scale-mat])
                normal-mat (-> model m/invert m/transpose mat/matrix44->matrix33)]
            (gl game uniformMatrix4fv u_p_v false (f32-arr (vec p*v)))
            (gl game uniformMatrix4fv u_model false (f32-arr (vec model)))
            (gl game uniformMatrix3fv u_normal_mat false (f32-arr (vec normal-mat)))
            (gl game drawArrays (gl game TRIANGLES) 0 36)))))))

(def system
  [shader/system
   gl-magic/system
   projection/system
   firstperson/system
   {::world/init-fn init-fn
    ::world/after-load-fn after-load-fn
    ::world/rules rules
    ::world/render-fn render-fn}])

(comment
  (o/query-all (:world hmm) ::esses)

  :-)
