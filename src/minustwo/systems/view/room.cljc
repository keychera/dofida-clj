(ns minustwo.systems.view.room
  (:require
   [engine.world :as world]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.gl.shader :as shader]
   [minustwo.gl.texture :as texture]
   [minustwo.gl.vao :as vao]
   [minustwo.systems.view.camera :as camera]
   [minustwo.systems.window :as window]
   [odoyle.rules :as o]))

(def rules
  (o/ruleset
   {::data
    [:what
     [::world/global ::gl-system/context ctx]
     [::world/global ::vao/db* vao-db*]
     [::world/global ::texture/db* texture-db*]
     [::world/global ::shader/all all-shaders]
     [::world/global ::window/dimension window]
     [::world/global ::camera/projection-matrix project]
     [::world/global ::camera/view-matrix player-view]
     [::world/global ::camera/position player-pos]]

    ::gl-data
    [:what
     [::world/global ::gl-system/context ctx]
     [::world/global ::vao/db* vao-db*]
     [::world/global ::shader/all all-shaders]]}))

(def system
  {::world/rules rules})