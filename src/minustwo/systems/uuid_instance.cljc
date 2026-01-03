(ns minustwo.systems.uuid-instance
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::is-uuid? boolean?)
(s/def ::remove boolean?)

(defn esse-inst [world & facts]
  (o/insert world (random-uuid) (merge {::is-uuid? true} (apply utils/deep-merge facts))))

(defn remove-esse-inst [world esse-uuid]
  (o/insert world esse-uuid ::remove true))

(defn before-load-fn [world _game]
  (println "removing" (count (o/query-all world ::all-inst)) "uuid instances")
  (-> world
      (o/insert ::all ::remove true)
      (o/fire-rules)
      (o/retract ::all ::remove)))

;; this was a dangerous realm to be in. be careful. look around.
;; query some more data until you are satisfied. wait, no, never be satisfied.

(def rules
  (o/ruleset
   {::all-inst
    [:what
     [instance-uuid ::is-uuid? true]]

    ::remove-self
    [:what
     [instance-uuid ::is-uuid? true]
     [instance-uuid ::remove true]
     [instance-uuid attr _]
     :then
     (s-> session (o/retract instance-uuid attr))]

    ::remove-all-uuid-instance
    [:what
     [instance-uuid ::is-uuid? true]
     [::all ::remove true]
     [instance-uuid attr _]
     :then
     (s-> session (o/retract instance-uuid attr))]}))

(def system
  {::world/rules #'rules
   ::world/before-load-fn #'before-load-fn})
