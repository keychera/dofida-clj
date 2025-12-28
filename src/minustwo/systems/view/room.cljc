(ns minustwo.systems.view.room
  (:require
   [engine.world :as world]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.gl.shader :as shader]
   [minustwo.gl.texture :as texture]
   [minustwo.gl.vao :as vao]
   [minustwo.systems.view.firstperson :as firstperson]
   [minustwo.systems.view.projection :as projection]
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
     [::world/global ::projection/matrix project]
     [::firstperson/player ::firstperson/look-at player-view]
     [::firstperson/player ::firstperson/position player-pos]]}))

(def system
  {::world/rules rules})