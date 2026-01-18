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

;; dev-repl only
(defn set-mode [mode]
  (swap! world* o/insert ::world/global ::mode mode))

(comment
  (require '[clj-memory-meter.core :as mm]
           '[com.phronemophobic.viscous :as viscous])

  (viscous/inspect #_the @world*)
  (viscous/inspect (o/query-all @world*))
  (mm/measure #_the @world*)
  (mm/measure (o/query-all @world*))

  ;; if we just call swap! and return it to the repl, 
  ;; the entirety of the world will be printed to repl, slowing the game down
  ;; if we consume it like this, the problem goes away 
  ;; + awareness of our game runtime memory size
  (mm/measure (set-mode ::pause))
  (mm/measure (set-mode ::render))

  (o/query-all @world* ::studio/let-me-capture-your-cuteness)

  (do (swap! world* o/insert ::world/global ::snap 1)
      :snap!)

  :-)
