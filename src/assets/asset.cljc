(ns assets.asset
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [clojure.spec.alpha :as s]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::asset-to-load any?)
(s/def ::type #{::texture})

;; data
(s/def ::use keyword?)
(s/def ::texture any?)

;; asset
(s/def ::loaded? boolean?)
(s/def ::metadata map?)

(s/def ::db* (partial instance? #?(:clj clojure.lang.Atom :cljs Atom)))

(def system
  {::world/init-fn
   (fn [world _game]
     (o/insert world ::global ::db* (atom {})))

   ::world/rules
   (o/ruleset
    {::db*
     [:what [::global ::db* db*]]

     ::to-load
     [:what
      [asset-id ::loaded? false]
      [asset-id ::metadata metadata]
      [::global ::db* db*]
      :then
      (println "registering metadata")
      (swap! db* assoc asset-id metadata)]})})

(defn asset
  [world asset-id & maps]
  (try
    (-> world
        (o/insert asset-id ::loaded? false)
        (o/insert asset-id ::metadata (apply utils/deep-merge maps)))
    (catch #?(:clj Exception :cljs js/Error) err
      #?(:clj  (println err)
         :cljs (js/console.error err))
      world)))

(defmulti process-asset (fn [_world* _game _asset-id asset-data] (::type asset-data)))

(defmethod process-asset :default
  [_world* _game asset-id {:keys [asset-type] :as _asset_data}]
  (println "asset(" asset-id ") has unhandled type (" asset-type ")"))

(defn load-asset [world* game]
  (let [world @world*
        db    @(:db* (first (o/query-all world ::db*)))]
    (doseq [{:keys [asset-id]} (o/query-all world ::to-load)]
      (let [asset-data (get db asset-id)]
        (println "loading" (::type asset-data) "asset for" asset-id)
        (process-asset world* game asset-id asset-data)))))

(defmethod process-asset ::texture
  [world* game asset-id {::keys [asset-to-load]}]
  (utils/get-image
   asset-to-load
   (fn on-image-load [{:keys [data width height]}]
     (let [texture-unit #_(swap! (:tex-count game) inc) 0 ;; disabling multi texture for reloading-ease
           texture      (gl game #?(:clj genTextures :cljs createTexture))]

       (gl game activeTexture (+ (gl game TEXTURE0) texture-unit))
       (gl game bindTexture (gl game TEXTURE_2D) texture)

       (gl game texImage2D (gl game TEXTURE_2D)
           #_:mip-level    0
           #_:internal-fmt (gl game RGBA)
           (int width)
           (int height)
           #_:border       0
           #_:src-fmt      (gl game RGBA)
           #_:src-type     (gl game UNSIGNED_BYTE)
           data)

       (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MAG_FILTER) (gl game NEAREST))
       (gl game texParameteri (gl game TEXTURE_2D) (gl game TEXTURE_MIN_FILTER) (gl game NEAREST))
       (swap! world*
              (fn [world]
                (-> world
                    (o/insert asset-id
                              {::loaded? true
                               ::texture
                               {:texture-unit texture-unit
                                :texture texture}}))))))))

