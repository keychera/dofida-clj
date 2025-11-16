(ns assets.asset
  (:require 
   [clojure.spec.alpha :as s]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]))

(s/def ::asset-to-load any?)
(s/def ::type #{::texture ::atlas ::alive})

;; data
(s/def ::use keyword?)

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
        db*   (:db* (first (o/query-all world ::db*)))]
    (doseq [{:keys [asset-id]} (o/query-all world ::to-load)]
      (let [asset-data (get @db* asset-id)]
        (println "loading" (::type asset-data) "asset for" asset-id)
        ;; i dont quite like assoc'ing db here
        (process-asset world* (assoc game ::db* db*) asset-id asset-data)))))
