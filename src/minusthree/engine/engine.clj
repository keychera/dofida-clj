(ns minusthree.engine.engine
  (:require
   [clojure.spec.alpha :as s]
   [minusthree.engine.ffm.arena :as arena]
   [minusthree.engine.input :as input]
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
  ;; refresh is dev time niceties to recall fn after-refresh without destroy, and rebuild arena
  ;; return true to trigger after-refresh
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

(declare init after-refresh tick before-refresh destroy)

(defn game-loop
  [{::keys [we-begin-the-game
            do-we-stop? do-we-refresh?
            things-from-out-there
            the-game-ends]
    :as game-loop-config}]
  (s/assert ::game-loop-config game-loop-config)
  (try
    (loop [game (we-begin-the-game) first-init? true]
      (let [[loop-state game']
            (with-open [game-arena (Arena/ofConfined)]
              (loop [game' (-> game
                               (assoc ::arena/game-arena game-arena)
                               (cond-> first-init? (init))
                               (after-refresh))]
                (cond
                  (do-we-stop? game')    [::stopping   (-> game' before-refresh destroy)]
                  (do-we-refresh? game') [::refreshing (-> game' before-refresh)]
                  :else (let [updated-game (things-from-out-there game')]
                          (recur (tick updated-game))))))]
        (condp = loop-state
          ::refreshing (recur game' false)
          ::stopping   ::ending)))
    (finally
      (the-game-ends))))

(defn init [{:keys [::world/this] :as game}]
  (->> (or this (world/init-world game systems/all))
       (loading/init-channel)
       (s/assert ::init-game)))

(defn after-refresh [new-game]
  (->> new-game
       (rendering/init)
       (input/init)
       (world/after-refresh)))

(defn tick [game]
  (-> game
      (update ::world/this o/fire-rules)
      (loading/loading-zone)
      (input/input-zone)
      (rendering/rendering-zone)))

(defn before-refresh [old-game]
  (rendering/destroy old-game)
  (world/before-refresh old-game)
  old-game)

(defn destroy [game]
  (-> game))
