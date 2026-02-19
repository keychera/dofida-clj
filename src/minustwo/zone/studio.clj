(ns minustwo.zone.studio
  (:require
   [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! s->]]
   [engine.utils :as utils]
   [engine.world :as world]
   [minustwo.gl.cljgl :as cljgl]
   [minusthree.gl.constants :refer [GL_COLOR_ATTACHMENT1
                                  GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT
                                  GL_FRAMEBUFFER GL_RGBA GL_UNSIGNED_BYTE]]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.stage.esse-model :as esse-model]
   [minustwo.stage.pseudo.offscreen :as offscreen]
   [minustwo.systems.time :as time]
   [minustwo.systems.view.room :as room]
   [minustwo.zone.render :as render]
   [odoyle.rules :as o]
   [thi.ng.geom.vector :as v])
  (:import
   [org.lwjgl.stb STBImageWrite]
   [org.lwjgl.system MemoryUtil]))

(s/def ::snap int?)
(s/def ::framecount int?)

(s/def ::fbo-data (s/keys :req-un [::fbo ::width ::height]))
(s/def ::fbo int?) ;; todo webgl type
(s/def ::width int?)
(s/def ::height int?)

(defn take-a-photo
  ([ctx fbo-data] (take-a-photo ctx fbo-data ".zzz/render.png"))
  ([ctx fbo-data target-path]
   (s/assert ::fbo-data fbo-data)
   (let [{:keys [fbo color-attachment width height]} fbo-data
         bytebuf (MemoryUtil/memAlloc (* width height 4))]
     (gl ctx bindFramebuffer GL_FRAMEBUFFER fbo)
     (gl ctx readBuffer color-attachment)
     (gl ctx readPixels 0 0 width height GL_RGBA GL_UNSIGNED_BYTE bytebuf)
     (STBImageWrite/stbi_flip_vertically_on_write true)
     (STBImageWrite/stbi_write_png target-path width, height, 4, bytebuf, (* width 4))
     (MemoryUtil/memFree bytebuf))))

(defn prepare-recording [world ctx models {:keys [framecount width height duration-sec fps]}]
  (let [{studio-fbo :fbo :as studio-fbo-data}
        (offscreen/prep-offscreen-render ctx (* 2 width) (* 2 height) 8
                                         {:color-attachment GL_COLOR_ATTACHMENT1})
        framecount (or framecount (int (Math/ceil (* fps duration-sec))))
        frametime  (/ 1000 fps)]
    (-> world
        (o/insert ::world/global {::snap framecount ;; not good, refactor later
                                  ::framecount framecount
                                  ::fbo-data studio-fbo-data})
        (o/insert ::time/now {::time/step-delay frametime})
        (o/insert ::recording ::esse-model/renderplay
                  (->>
                   [{:custom-fn
                     (fn quiet-on-set! [_world ctx]
                       (gl ctx bindFramebuffer GL_FRAMEBUFFER studio-fbo)
                       (gl ctx clearColor 0.02 0.02 0.12 1.0)
                       (gl ctx clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
                       (gl ctx viewport 0 0 width height))}

                    (eduction
                     (mapcat (fn [{:keys [esse-id]}] [{:prep-esse esse-id} {:render-esse esse-id}]))
                     models)

                    {:custom-fn
                     (fn quiet-on-set! [_world ctx]
                       (gl ctx drawBuffers (int-array [GL_COLOR_ATTACHMENT1])))}]
                   flatten (into []))))))

(def wait-until-frame 2)

(def rules
  (o/ruleset
   {::let-me-capture-your-cuteness
    [:what
     [::time/now ::time/slice 6]
     [::world/global ::snap tally {:then false}]
     [::world/global ::framecount framecount {:then false}]
     [::world/global ::fbo-data fbo-source {:then false}]
     [::world/global ::gl-system/context ctx]
     :then
     (if (<= tally 0)
       (s-> session
            (o/retract ::world/global ::snap)
            (o/retract ::world/global ::framecount))
       (let [framenum (- framecount tally)]
         (when (> framenum wait-until-frame) ;; just heuristic because we dont know when render is ready
           (let [framenum (- framenum wait-until-frame)
                 framefile (format ".zzz/out/render-%04d.png" framenum)]
             (println "[studio] out:" framefile)
             (take-a-photo ctx fbo-source framefile)))
         (insert! ::world/global ::snap (dec tally))))]}))

;; don't you ever lose the sight of what's important to you 

(def system
  {::world/rules #'rules})
