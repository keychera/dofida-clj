(ns minustwo.systems.view.room
  (:require
   [engine.world :as world]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.systems.view.firstperson :as firstperson]
   [minustwo.systems.view.projection :as projection]
   [odoyle.rules :as o]))

(def rules
  (o/ruleset
   {::data
    [:what
     [::world/global ::gl-system/context ctx]
     [::world/global ::projection/matrix project]
     [::firstperson/player ::firstperson/look-at player-view]
     [::firstperson/player ::firstperson/position player-pos]]}))

(def system
  {::world/rules rules})