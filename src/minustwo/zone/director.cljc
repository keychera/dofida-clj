(ns minustwo.zone.director
  (:require
   [clojure.spec.alpha :as s]
   [engine.game :as game]
   [engine.macros :refer [s->]]
   [engine.utils :as utils]
   [engine.world :as world]
   [minustwo.gl.gl-system :as gl-system]
   [minustwo.stage.pmx-renderer :as pmx-renderer]
   [minustwo.systems.time :as time]
   [minustwo.systems.view.room :as room]
   [minustwo.systems.window :as window]
   [minustwo.zone.render :as render]
   [minustwo.zone.studio :as studio]
   [odoyle.rules :as o]))

(s/def ::mode #{::pause ::render ::recording})
(s/def ::rec-session (s/keys :req-un [::fps ::duration-sec]))

(s/def ::fps int?)
(s/def ::duration-sec number?)

(defn init-fn [world _game]
  (-> world
      (o/insert ::world/global ::mode ::render)
      (o/insert ::time/now ::time/step-delay (/ 1000 6))))

(defn after-load-fn [world game]
  #_{:clj-kondo/ignore [:inline-def]} ;; for repl goodness
  (def world* (::world/atom* game))
  world)

(def rules
  (o/ruleset
   {::mode
    [:what
     [::world/global ::mode mode]]

    ::rec-session
    [:what
     [::world/global ::rec-session rec-config]
     [::world/global ::gl-system/context ctx]
     [::world/global ::window/dimension window]
     :then
     ;; models retrieval is complected for now
     (let [models (o/query-all session ::pmx-renderer/render-data)
           rec-session (merge window rec-config)]
       (s-> session
            (o/insert ::world/global ::mode ::recording)
            (o/insert ::time/now ::time/scale (:timescale rec-config))
            (o/retract ::world/global ::rec-session)
            (studio/prepare-recording ctx models rec-session)))]}))

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
      ::recording
      (let [total-time (:total-time game)
            delta-time (:delta-time game)
            world      (swap! (::world/atom* game)
                              (fn [world] (-> world (time/insert total-time delta-time) (o/fire-rules))))
            ctx        (:ctx (utils/query-one world ::room/data))]
        (when ctx
          (doseq [render-fn @(::game/render-fns* game)]
            (render-fn world game))))
      :no-op)))

;; dev-repl only
(defn set-mode! [mode]
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
  (mm/measure (set-mode! ::pause))
  (mm/measure (set-mode! ::render))

  (mm/measure
   (swap! world* o/insert ::world/global
          {::rec-session {:fps 24 :duration-sec 5 :timescale (/ 1 4)}}))

  (o/query-all @world* ::studio/let-me-capture-your-cuteness)

  (do (swap! world* o/insert ::world/global ::snap 1)
      :snap!)

  :-)
