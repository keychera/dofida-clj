(ns minusthree.engine.rendering.playground
  (:require
   [minusthree.engine.ffm.arena :as arena]
   [minusthree.engine.loader :as loader]
   [minusthree.engine.rendering.par-streamlines :refer [parsl-context||]])
  (:import
   [box2d b2d]
   [java.lang.foreign Arena]
   [thorvg tvg]))

(defonce _loadlib
  (do (loader/load-libs "box2dd")
      (loader/load-libs "libthorvg-1")))

(defn init [{:keys [::arena/game-arena] :as game}]
  (let []
    (tvg/tvg_engine_init 4)
    game))

(defn destroy [{}]
  (println "destroy playground")
  (tvg/tvg_engine_term))

(comment
  (loader/load-libs "par_streamlines")
  (with-open [arena (Arena/ofConfined)]
    (let [a (parsl-context|| arena)]
      (println (type a))))

  (loader/load-libs "box2dd")
  (type b2d)
  (with-open [arena (Arena/ofConfined)]
    (let [a (b2d/b2DefaultWorldDef arena)]
      (println (type a))))

  (type tvg)
  (loader/load-libs "libthorvg-1")
  (tvg/tvg_engine_init 4)
  (tvg/tvg_engine_term)

  :-)
