(ns rules.asset
  (:require 
   [clojure.spec.alpha :as s]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::asset-to-load any?)
(s/def ::type #{::texture-from-png
                ::fbo
                ::alive})

;; data
(s/def ::use keyword?)

;; asset
(s/def ::loaded? boolean?)
(s/def ::asset-data map?)

(def system
  {::world/rules
   (o/ruleset
    {::to-load
     [:what
      [asset-id ::loaded? false]
      [asset-id ::asset-data asset-data]]})})

(defn asset
  [world asset-id & maps]
  (try
    (-> world
        (o/insert asset-id ::loaded? false)
        (o/insert asset-id ::asset-data (apply utils/deep-merge maps)))
    (catch #?(:clj Exception :cljs js/Error) err
      #?(:clj  (println err)
         :cljs (js/console.error err))
      world)))

(defmulti process-asset (fn [_world* _game _asset-id asset-data] (::type asset-data)))

(defmethod process-asset :default
  [_world* _game asset-id {:keys [asset-type] :as _asset_data}]
  (println "asset(" asset-id ") has unhandled type (" asset-type ")"))

(defn load-asset [world* game]
  (let [world @world*]
    (doseq [{:keys [asset-id asset-data]} (o/query-all world ::to-load)]
      (println "loading" (::type asset-data) "asset for" asset-id)
      (process-asset world* game asset-id asset-data))))
