(ns minustwo.stage.pseudo.studio
  (:require
   #?(:clj  [minustwo.gl.macros :refer [lwjgl] :rename {lwjgl gl}]
      :cljs [minustwo.gl.macros :refer [webgl] :rename {webgl gl}])
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! s->]]
   [engine.world :as world]
   [minustwo.gl.constants :refer [GL_FRAMEBUFFER GL_RGBA GL_UNSIGNED_BYTE]]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.systems.time :as time]
   [minustwo.systems.view.room :as room]
   [odoyle.rules :as o])
  #?(:clj (:import
           [org.lwjgl.stb STBImageWrite]
           [org.lwjgl.system MemoryUtil])))

(s/def ::snap int?)

(s/def ::fbo-data (s/keys :req-un [::fbo ::width ::height]))
(s/def ::fbo int?) ;; todo webgl type
(s/def ::width int?)
(s/def ::height int?)

(defn take-a-photo [ctx fbo-data]
  ;; hardcoded for now
  (s/assert ::fbo-data fbo-data)
  #?(:cljs [gl GL_FRAMEBUFFER GL_RGBA GL_UNSIGNED_BYTE ctx fbo-data :noop]
     :clj
     (let [{:keys [fbo color-attachment width height]} fbo-data
           bytebuf (MemoryUtil/memAlloc (* width height 4))]
       (gl ctx bindFramebuffer GL_FRAMEBUFFER fbo)
       (gl ctx readBuffer color-attachment)
       (gl ctx readPixels 0 0 width height GL_RGBA GL_UNSIGNED_BYTE bytebuf)
       (STBImageWrite/stbi_flip_vertically_on_write true)
       (STBImageWrite/stbi_write_png ".zzz/render.png" width, height, 4, bytebuf, (* width 4))
       (MemoryUtil/memFree bytebuf))))

(defn after-load-fn [world game]
  #_{:clj-kondo/ignore [:inline-def]} ;; for repl goodness
  (def world* (::world/atom* game))
  world)

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
  {::world/after-load-fn #'after-load-fn
   ::world/rules #'rules})

(comment

  (o/query-all @world* ::let-me-capture-your-cuteness)

  (do (swap! world* o/insert ::world/global ::snap 1)
      :snap!)

  :-)