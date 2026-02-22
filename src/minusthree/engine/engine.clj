(ns minusthree.engine.engine
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.ffm.arena :as arena]
   [minusthree.engine.loading :as loading]
   [minusthree.engine.rendering :as rendering]
   [minusthree.engine.systems :as systems]
   [minusthree.engine.time :as time]
   [minusthree.engine.world :as world]
   [odoyle.rules :as o])
  (:import
   [java.lang.foreign Arena]))

(s/def ::init-game (s/keys :req [::world/this ::time/total]))

(s/def
  ::we-begin-the-game ;; by providing an
  fn?
  ;; of zero arity that return the initial game state
  ;; this may have side effect of anything you wish
  )

(s/def ;; we ask ourself
  ::do-we-stop? ;; by calling this
  fn?
  ;; of one arity, accepting the game state, returning bool
  ;; return true to stop the game
  )

(s/def ;; we ask ourself
  ::do-we-refresh? ;; by calling this
  fn?
  ;; of one arity, accepting the game state, returning bool
  ;; refresh is dev time niceties to recall fn declared by ::world/post-fn
  ;; return true to trigger ::world/post-fn
  )

(s/def ;; the game will loop and
  ;; the engine will gather every
  ::things-from-out-there ;; by calling an
  fn?
  ;; of one arity, accepting the game state, returning the updated game
  ;; this might include timing, input, platform changes, dev time flags, etc.
  )

(s/def ;; lastly
  ::the-game-ends ;; the engine will call an
  fn? ;; of zero arity to do cleanup 
  )

(s/def ::game-loop-config 
  (s/keys :req [::we-begin-the-game
                ::do-we-stop?
                ::do-we-refresh?
                ::things-from-out-there
                ::the-game-ends]))

;; TODO design, currently we don't allow anything to leak 
;; outside of the game loop except via exception
;; devtime, that will be catch by minusthree.-dev.start
;; and exception will be presented by viscous/inspect

(declare init tick destroy)

(defn game-loop
  [{::keys [we-begin-the-game
            do-we-stop? do-we-refresh?
            things-from-out-there
            the-game-ends]
    :as game-loop-config}]
  (s/assert ::game-loop-config game-loop-config)
  (try
    (with-open [game-arena (Arena/ofConfined)]
      (loop [game (-> (we-begin-the-game)
                      (assoc ::arena/game-arena game-arena)
                      (init)
                      (world/post-world))]
        (if-not (do-we-stop? game)
          (let [updated-game
                (cond-> (things-from-out-there game)
                  (do-we-refresh? game) (world/post-world))]
            (recur (tick updated-game)))
          (destroy game))))
    (finally
      (the-game-ends))))

(defn init [game]
  (->> (world/init-world game systems/all)
       (loading/init-channel)
       (rendering/init)
       (s/assert ::init-game)))

(defn tick [game]
  (-> game
      (update ::world/this o/fire-rules)
      (loading/loading-zone)
      (rendering/rendering-zone)))

(defn destroy [game]
  (-> game
      (rendering/destroy)))
