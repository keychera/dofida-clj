(ns engine.engine
  #_{:clj-kondo/ignore [:unused-referred-var :unused-namespace]}
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   #?(:clj  [minusone.rules.model.assimp-jvm :as assimp-jvm]
      :cljs [minusone.rules.model.assimp-js :as assimp-js])
   [assets.asset :as asset]
   [com.rpl.specter :as sp]
   [engine.refresh :refer [*refresh?]]
   [engine.utils :as utils]
   [engine.world :as world]
   [minusone.rules.gizmo.perspective-grid :as perspective-grid]
   [minusone.rules.gl.gl :refer [GL_BLEND GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT]]
   [minusone.rules.gl.magic :as gl-magic]
   [minusone.rules.gl.shader :as shader]
   [minusone.rules.gl.texture :as texture]
   [minusone.rules.model.assimp :as assimp]
   [minusone.rules.projection :as projection]
   [minusone.rules.transform3d :as t3d]
   [minusone.rules.view.firstperson :as firstperson]
   [odoyle.rules :as o]
   [minustwo.systems.input :as input]
   [rules.time :as time]
   [rules.window :as window]))

(defn ->game [context]
  (merge
   {:context context}
   {::render-fns* (atom nil)}
   (world/->init)))

(def all-systems
  (flatten
   [time/system

    #?(:clj  assimp-jvm/system
       :cljs assimp-js/system)

    asset/system
    window/system

    texture/system

    gl-magic/system
    shader/system
    projection/system
    input/system
    firstperson/system
    t3d/system

    perspective-grid/system]))

(defn init [game]
  (println "init game")
  (gl game enable GL_BLEND)

  (let [{:keys [all-rules before-fns init-fns after-fns render-fns]} (world/build-systems all-systems)
        [width height]                                               (utils/get-size game)]
    (reset! (::render-fns* game) render-fns)
    (swap! (::world/atom* game)
           (fn [world]
             (-> (world/init-world world game all-rules before-fns init-fns after-fns)
                 (window/set-window width height)
                 (o/fire-rules))))
    (asset/load-asset (::world/atom* game) game)))

(defn tick [game]
  (if @*refresh?
    (try (println "refresh game")
         (reset! *refresh? false)
         (init game)
         #?(:clj  (catch Exception err (throw err))
            :cljs (catch js/Error err
                    (utils/log-limited err "[init-error]"))))
    (try
      #_(let [#_"the loading zone"
            world*           (::world/atom* game)
            models-to-load   (o/query-all @world* ::assimp/load-with-assimp)
            data-uri-to-load (o/query-all @world* ::texture/uri-to-load)]
        (if (or (seq models-to-load) (seq data-uri-to-load))
          #?(:clj (some-> data-uri-to-load (texture/load-texture->world* (::world/atom* game) game))
             :cljs (do (some-> models-to-load (assimp-js/load-models-from-world* (::world/atom* game)))
                       (some-> data-uri-to-load (texture/load-texture->world* (::world/atom* game) game))))
          (let [{:keys [total-time
                        delta-time]} game
                [width height]       (utils/get-size game)
                world                (swap! (::world/atom* game)
                                            #(-> %
                                                 (time/insert total-time delta-time)
                                                 (o/fire-rules)))]
            (gl game blendFunc GL_SRC_ALPHA GL_ONE_MINUS_SRC_ALPHA)
            (gl game clearColor 0.02 0.02 0.12 1.0)
            (gl game clear (bit-or GL_COLOR_BUFFER_BIT GL_DEPTH_BUFFER_BIT))
            (gl game viewport 0 0 width height)

            (doseq [render-fn @(::render-fns* game)]
              (render-fn world game)))))
      #?(:clj  (catch Exception err (throw err))
         :cljs (catch js/Error err
                 (utils/log-limited err "[tick-error]")))))
  game)

