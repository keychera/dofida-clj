(ns minustwo.zone.director
  (:require
   [clojure.spec.alpha :as s]
   [engine.utils :as utils]
   [engine.world :as world]
   [minustwo.zone.render :as render]
   [minustwo.zone.studio :as studio]
   [odoyle.rules :as o]))

(s/def ::mode #{::pause ::render ::recording})

(defn init-fn [world _game]
  (o/insert world ::world/global ::mode ::render))

(defn after-load-fn [world game]
  #_{:clj-kondo/ignore [:inline-def]} ;; for repl goodness
  (def world* (::world/atom* game))
  world)

(def rules
  (o/ruleset
   {::mode
    [:what
     [::world/global ::mode mode]]}))

(def system
  {::world/init-fn #'init-fn
   ::world/after-load-fn #'after-load-fn
   ::world/rules #'rules})

#_#_"(" ("chaotic. deterministic. moral. suboptimal. #_. looks like you forgot something")
;; check your pocket again. maybe that's where your dream is.

(defn rolling! [game]
  (let [world @(::world/atom* game)
        mode  (:mode (utils/query-one world ::mode))]
    (condp = mode
      ::render (render/render-zone game)
      :no-op)))

(comment

  (swap! world* o/insert ::world/global ::mode ::pause)
  (swap! world* o/insert ::world/global ::mode ::render)

  (o/query-all @world* ::studio/let-me-capture-your-cuteness)

  (do (swap! world* o/insert ::world/global ::snap 1)
      :snap!)

  :-)
