(ns minusthree.engine.loading
  (:require
   [clojure.core.async :refer [#?@(:clj [io-thread >!!]) chan poll!]]
   [clojure.spec.alpha :as s]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::channel some? #_ManyToManyChannel)
(s/def ::load-fn fn? #_(fn [] (s/coll-of facts)))
(s/def ::state #{:pending :loading :success})
(def channel-size 8)

(defn insert-load-fn [world esse-id load-fn]
  (o/insert world esse-id {::load-fn load-fn ::state :pending}))

(defn init-channel [game]
  (assoc game ::channel (chan channel-size)))

(def rules
  (o/ruleset
   {::to-load
    [:what
     [esse-id ::load-fn load-fn]
     [esse-id ::state :pending]]}))

(def system
  {::world/rules #'rules})

(defn loading-zone [game]
  (let [loading-ch (::channel game)
        new-load   (poll! loading-ch)]
    (if new-load
      (let [{:keys [esse-id new-facts]} new-load]
        (update game ::world/this
                (fn [world]
                  (-> (reduce o/insert world new-facts)
                      (o/insert esse-id ::state :success)))))
      (let [world    (::world/this game)
            to-loads (into [] (take channel-size) (o/query-all world ::to-load))]
        #?(:clj (doseq [{:keys [esse-id load-fn]} to-loads]
                  (io-thread
                   (let [loaded-facts (load-fn)] ;; TODO error handling
                     (>!! loading-ch {:esse-id esse-id :new-facts loaded-facts})))))
        (update game ::world/this
                (fn [world]
                  (reduce (fn [w' {:keys [esse-id]}] (o/insert w' esse-id ::state :loading)) world to-loads)))))))
