(ns minusthree.engine.rendering.playground
  (:require
   [minusthree.engine.ffm.arena :as arena]
   [minusthree.engine.loader :as loader]
   [minusthree.engine.rendering.par-streamlines :refer [parsl-context||]]
   [com.phronemophobic.viscous :as viscous])
  (:import
   [box2d b2d]
   [java.lang.foreign Arena MemoryLayout]
   [thorvg tvg]))

(defonce _loadlib
  (do (loader/load-libs "box2dd")
      (loader/load-libs "libthorvg-1")))

;; https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/MemorySegment.html
;; https://www.thorvg.org/native-tutorial
;; thorvg capi https://github.com/thorvg/thorvg.example/blob/main/src/Capi.cpp

(defn init [{:keys [::arena/game-arena] :as game}]
  (tvg/tvg_engine_init 4)
  (let [WIDTH    20
        HEIGHT   20
        buffer|| (.allocate ^Arena game-arena (MemoryLayout/sequenceLayout (* WIDTH HEIGHT) tvg/C_INT))
        canvas|| (doto (tvg/tvg_swcanvas_create (tvg/TVG_ENGINE_OPTION_DEFAULT))
                   (tvg/tvg_swcanvas_set_target buffer|| WIDTH WIDTH HEIGHT (tvg/TVG_COLORSPACE_ABGR8888)))
        rect||   (doto (tvg/tvg_shape_new)
                   (tvg/tvg_shape_append_rect #_x-y 5 5 #_w-h 10 10 #_rx-ry 2 2 #_cw? false)
                   (tvg/tvg_shape_set_fill_color #_rgba 100 100 100 127))] ;; not 255 since java use unsigned bytes,  -128 .. 127
    (tvg/tvg_canvas_add canvas|| rect||)
    (tvg/tvg_canvas_draw canvas|| #_clear true)
    (tvg/tvg_canvas_sync canvas||)
    #_{:clj-kondo/ignore [:inline-def]}
    (def debug-var (vec (.toArray buffer|| tvg/C_INT)))
    (println "playground ready!")
    game))

(defn render [{::keys []}])

(defn destroy [{}]
  (println "destroy playground")
  (tvg/tvg_engine_term))

(comment
  (viscous/inspect debug-var)

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
