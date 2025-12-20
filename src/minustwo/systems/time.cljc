(ns minustwo.systems.time
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert! s->]]
   [engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::now number?)
(s/def ::raw-total number?)
(s/def ::raw-delta number?)
(s/def ::total number?)
(s/def ::delta number?)
(s/def ::scale number?)

(s/def ::step number?)
(s/def ::step-delay number?)
(s/def ::slice int?)

(def fps 60)
(def timestep-ms (/ 1000 fps))

(def system
  {::world/init-fn
   (fn [world _game]
     (o/insert world ::now {::scale 1.0
                            ::total 0.0
                            ::step 1
                            ::step-delay timestep-ms}))

   ::world/rules
   (o/ruleset
    {::real-world
     [:what
      [::now ::raw-total rt]
      [::now ::raw-delta rd]
      [::now ::scale timescale {:then false}]
      [::now ::total tt {:then false}]
      :then
      (let [dt (* rd timescale)]
        (insert! ::now {::total (+ tt dt) ::delta dt}))]

     ::fixed-timestep
     [:what
      [::now ::raw-delta dt]
      [::now ::step timestep {:then false}]
      [::now ::step-delay step-delay-ms {:then false}]
      :then
      (if (< step-delay-ms 0)
        (s-> session
             (o/insert ::now ::step-delay timestep-ms)
             (o/insert ::now ::step (inc timestep)))
        (insert! ::now ::step-delay (- step-delay-ms dt)))]

     ::slice-per-step
     [:what
      [::now ::step _]
      :then
      (insert! ::now ::slice 0)]

     ::slicing
     [:what
      [::now ::slice slice]
      :when (< slice 6)
      :then
      (insert! ::now ::slice (inc slice))]})})

(defn insert [world total delta]
  (o/insert world ::now {::raw-total total ::raw-delta delta}))