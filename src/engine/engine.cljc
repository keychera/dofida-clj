(ns engine.engine
  (:require
   [engine.refresh :refer [*refresh?]]
   [minustwo.zone.init :refer [init-zone]]
   [minustwo.zone.loading :refer [loading-zone]]
   [minustwo.zone.render :refer [render-zone]]))

(declare refresh-zone progress-zone error-zone)

(defn tick [game]
  (try
    (if @*refresh?
      (refresh-zone game)
      (progress-zone game))
    (catch #?(:clj Exception :cljs js/Error) err
      (error-zone game err)))
  game)

(defn init [game] (init-zone game))

(defn refresh-zone [game]
  (reset! *refresh? false)
  (init game))

(defn progress-zone [game]
  (loading-zone game)
  (render-zone game))

(defn error-zone [_game err]
  (throw err))
