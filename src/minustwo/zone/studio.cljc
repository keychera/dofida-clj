(ns minustwo.zone.studio
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [engine.game :refer [gl-ctx]]
   [engine.macros :refer [insert! s->]]
   [engine.utils :as utils]
   [engine.world :as world]
   [minustwo.gl.cljgl :as cljgl]
   [minustwo.gl.constants :refer [GL_COLOR_ATTACHMENT1 GL_COLOR_BUFFER_BIT
                                  GL_DEPTH_BUFFER_BIT GL_FRAMEBUFFER GL_RGBA
                                  GL_UNSIGNED_BYTE]]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.stage.esse-model :as esse-model]
   [minustwo.stage.pseudo.offscreen :as offscreen]
   [minustwo.systems.time :as time]
   [minustwo.systems.view.room :as room]
   [minustwo.zone.render :as render]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v])
  #?(:clj (:import
           [org.lwjgl.stb STBImageWrite]
           [org.lwjgl.system MemoryUtil])))

(s/def ::snap int?)

(s/def ::fbo-data (s/keys :req-un [::fbo ::width ::height]))
(s/def ::fbo int?) ;; todo webgl type
(s/def ::width int?)
(s/def ::height int?)

(defn take-a-photo
  ([ctx fbo-data] (take-a-photo ctx fbo-data ".zzz/render.png"))
  ([ctx fbo-data target-path]
   (s/assert ::fbo-data fbo-data)
   #?(:cljs [gl GL_FRAMEBUFFER GL_RGBA GL_UNSIGNED_BYTE ctx fbo-data target-path :noop]
      :clj
      (let [{:keys [fbo color-attachment width height]} fbo-data
            bytebuf (MemoryUtil/memAlloc (* width height 4))]
        (gl ctx bindFramebuffer GL_FRAMEBUFFER fbo)
        (gl ctx readBuffer color-attachment)
        (gl ctx readPixels 0 0 width height GL_RGBA GL_UNSIGNED_BYTE bytebuf)
        (STBImageWrite/stbi_flip_vertically_on_write true)
        (STBImageWrite/stbi_write_png target-path width, height, 4, bytebuf, (* width 4))
        (MemoryUtil/memFree bytebuf)))))

(defn record [world game]
  (let [[width height] (utils/get-size game)
        ctx            (gl-ctx game)
        {studio-fbo :fbo :as studio-fbo-data}
        (offscreen/prep-offscreen-render ctx (* 2 width) (* 2 height) 1
                                         {:color-attachment GL_COLOR_ATTACHMENT1})
        ;; hardcoded for now
        duration-sec 60 fps 60
        dt (/ 1 fps) framecount (* fps duration-sec)]
    (-> world
        (o/insert ::world/global ::snap framecount)
        (o/insert ::recording ::esse-model/renderplay
                  [{:custom-fn
                    (fn quiet-on-set! [_world ctx]
                      (gl ctx bindFramebuffer GL_FRAMEBUFFER studio-fbo)
                      (gl ctx clearColor (/ 88 255) (/ 94 255) (/ 195 255) 1.0)
                      (gl ctx clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT)))}

                   {:custom-fn
                    (fn recording [_world _ctx]
                      (let [tt    (:total-time game)
                            world (swap! (::world/atom* game)
                                         (fn [world] (-> world (time/insert tt dt) (o/fire-rules))))]
                        (render/render world game)))}

                   {:custom-fn
                    (fn [world ctx]
                      (when-let [tally (:tally (utils/query-one world ::let-me-capture-your-cuteness))]
                        (take-a-photo ctx studio-fbo-data (str ".zzz/out/render-" (- framecount tally) ".png"))))}

                   {:custom-fn
                    (offscreen/render-fbo
                     studio-fbo-data {:fbo 0 :width width :height height}
                     {:translation (v/vec3 0.0 0.0 0.0)
                      :scale       (v/vec3 1.0)})}]))))

(def rules
  (o/ruleset
   {::let-me-capture-your-cuteness
    [:what
     [::time/now ::time/slice 6]
     [::world/global ::snap tally {:then false}]
     :then
     (if (<= tally 0)
       (s-> session (o/retract ::world/global ::snap))
       (insert! ::world/global ::snap (dec tally)))]}))

;; don't you ever lose the sight of what's important to you 

(def system
  {::world/rules #'rules})
