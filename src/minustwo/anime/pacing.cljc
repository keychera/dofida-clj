(ns minustwo.anime.pacing
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [minustwo.systems.time :as time]
   [odoyle.rules :as o]))

(s/def ::max-progress number?)
(s/def ::timescale number?)
(s/def ::config (s/keys :req-un [::max-progress ::timescale]))
(s/def ::progress number?)
(s/def ::loop int?)
(s/def ::debug some?)

(s/def ::fact (s/cat :id ::o/id :attr ::o/attr :value ::o/value))
(s/def ::timed-fact (s/cat :time-inp number?
                           :facts (s/coll-of ::fact :kind vector?)
                           :loop-fired int?))
(s/def ::fact-timeline (s/coll-of ::timed-fact :kind vector?))

(defn set-config [world config]
  (o/insert world ::world/global ::config
            (merge {:max-progress 16.0
                    :timescale    (/ 1 640)}
                   config)))

(defn insert-timeline [world esse-id timeline]
  (let [timeline' (into [] (map (fn [[time-inp facts]] [time-inp facts -1])) timeline)]
    (o/insert world esse-id ::fact-timeline timeline')))

(defn init-fn [world _game]
  (-> world
      (set-config {:max-progress 16.0
                   :timescale    (/ 1 640)})
      (o/insert ::world/global
                {::progress ##Inf
                 ::loop 0})
      #_(insert-timeline ::world/global
                         [[0.0 [["this thing" ::debug 99]]]
                          [8.0 [["hey2" ::debug 1]
                                ["hey8" ::debug 100]]]])))

(def rules
  (o/ruleset
   {::pacing
    [:what
     [::time/now ::time/total tt {:then false}]
     [::world/global ::progress prev-progress {:then false}]
     [::world/global ::loop loop-counter {:then false}]
     [::time/now ::time/slice 1]
     [::world/global ::config config]
     :then
     (let [{:keys [max-progress timescale]} config
           progress  (mod (* tt timescale) max-progress)
           new-loop? (< progress prev-progress)]
       (s-> session
            ((fn [s] (if new-loop?
                       (o/insert s ::world/global ::loop (inc loop-counter))
                       s)))
            (o/insert ::world/global ::progress progress)))]

    ::new-loop
    [:what
     [::world/global ::loop loop-counter]
     :then (println "loop #" loop-counter)]

    ::debug
    [:what [any-id ::debug any-val]
     :then (println "[debug]" any-id any-val)]

    ::facts-on-the-beat
    [:what
     [::world/global ::progress progress]
     [::world/global ::loop loop-counter {:then false}]
     [esse-id ::fact-timeline timeline {:then false}]
     :when (> loop-counter 0)
     :then
     (let [facts-and-timeline
           (into []
                 (map (fn [[time-inp facts loop-fired :as timed-fact]]
                        (if (and (< time-inp progress) (< loop-fired loop-counter))
                          [facts (assoc timed-fact 2 loop-counter)]
                          [nil timed-fact])))
                 timeline)
           facts     (into [] (comp (map first) (remove nil?) cat) facts-and-timeline)
           timeline' (into [] (map second) facts-and-timeline)]
       (s-> session
            ((fn [s'] (reduce (fn [a b] (o/insert a b)) s' facts)))
            (o/insert esse-id ::fact-timeline timeline')))]}))


#_("maybe it will (go) away ))( maybe (not). )(( we will (welcome) them with (open) arms")

(def system
  {::world/init-fn #'init-fn
   ::world/rules #'rules})
