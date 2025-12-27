(ns minustwo.stage.pmx-renderer
  (:require
   [engine.game :refer [gl-ctx]]
   [engine.world :as world :refer [esse]]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.shader :as shader]
   [minustwo.model.pmx-model :as pmx-model]
   [minustwo.systems.view.firstperson :as firstperson]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v]))

(def pmx-vert
  (str cljgl/version-str
       "
 precision mediump float;
 
 in vec3 POSITION;

 uniform mat4 u_model;
 uniform mat4 u_view;
 uniform mat4 u_projection;

 void main() {
   vec4 pos;
   pos = vec4(POSITION, 1.0);
   gl_Position = u_projection * u_view * u_model * pos;
 }
"))

(def pmx-frag
  (str cljgl/version-str
       "
 precision mediump float;
 
 out vec4 o_color;

 void main() {
   o_color = vec4(1.0); 
 }
"))

(defn init-fn [world game]
  (let [ctx (gl-ctx game)]
    (-> world
        (firstperson/insert-player (v/vec3 0.0 15.5 13.0) (v/vec3 0.0 0.0 -1.0))
        (esse ::pmx-shader #::shader{:program-info (cljgl/create-program-info-from-source ctx pmx-vert pmx-frag)})
        (esse ::silverwolf-pmx
              #::pmx-model{:model-path "assets/models/SilverWolf/SilverWolf.pmx"}
              #::shader{:use ::pmx-shader}))))

(def rules
  (o/ruleset
   {::render-data
    [:what
     [esse-id ::pmx-model/arbitraty data]
     :then
     (println esse-id "got" (keys data) "!")]}))

(defn render-fn [world _game]
  (let [{:keys [_data]} (o/query-all world ::render-data)]))


(def system
  {::world/init-fn init-fn
   ::world/rules rules
   ::world/render-fn render-fn})