(ns engine.engine
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
   [minusone.learnopengl :as learnopengl]
   [minusone.rules.model.assimp :as assimp]
   [minusone.rules.model.moon :as moon]
   [odoyle.rules :as o]
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

    moon/system

    learnopengl/system]))

(defn init [game]
  (println "init game")
  (gl game enable (gl game BLEND))

  (let [all-rules  (distinct (apply concat (sp/select [sp/ALL ::world/rules] all-systems)))
        init-fns   (sp/select [sp/ALL ::world/init-fn some?] all-systems)
        before-fns (sp/select [sp/ALL ::world/before-load-fn some?] all-systems)
        after-fns  (sp/select [sp/ALL ::world/after-load-fn some?] all-systems)
        render-fns (sp/select [sp/ALL ::world/render-fn some?] all-systems)
        [w h]      (utils/get-size game)]

    (reset! (::render-fns* game) render-fns)
    (swap! (::world/atom* game)
           (fn [world]
             (-> (world/init-world world game all-rules before-fns init-fns after-fns)
                 (window/set-window w h)
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
      (let [#_"the loading zone"
            world*           (::world/atom* game)
            models-to-load   (o/query-all @world* ::assimp/load-with-assimp)
            textures-to-load (o/query-all @world* ::assimp/gl-texture-to-load)]
        (if (or (seq models-to-load) (seq textures-to-load))
          #?(:clj nil
             :cljs (do (some-> models-to-load (assimp-js/load-models-from-world* (::world/atom* game)))
                       (some-> textures-to-load (assimp-js/load-texture-to-world* (::world/atom* game) game))))
          (let [{:keys [total-time
                        delta-time]} game
                [width height]       (utils/get-size game)
                world                (swap! (::world/atom* game)
                                            #(-> %
                                                 (time/insert total-time delta-time)
                                                 (o/fire-rules)))]
            (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))
            (gl game clearColor 0.02 0.02 0.12 1.0)
            (gl game clear (bit-or (gl game COLOR_BUFFER_BIT) (gl game DEPTH_BUFFER_BIT)))
            (gl game viewport 0 0 width height)

            (doseq [render-fn @(::render-fns* game)]
              (render-fn world game)))))
      #?(:clj  (catch Exception err (throw err))
         :cljs (catch js/Error err
                 (utils/log-limited err "[tick-error]")))))
  game)

