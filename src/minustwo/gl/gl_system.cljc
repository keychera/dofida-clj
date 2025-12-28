(ns minustwo.gl.gl-system
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [engine.world :as world]
   [minustwo.gl.shader :as shader]
   [odoyle.rules :as o]))

(s/def ::context #?(:clj some? :cljs #(instance? js/WebGL2RenderingContext %)))

#_{:clj-kondo/ignore [:unused-binding]}
(defn init-fn [world game]
  (let [gl-context #?(:clj {} :cljs (:webgl-context game))]
    (o/insert world ::world/global ::context gl-context)))

(def rules
  (o/ruleset
   {::collect-shaders
    [:what
     [esse-id ::shader/program-info program-info]
     :then-finally
     (let [collected   (o/query-all session ::collect-shaders)
           all-shaders (into {} (map (juxt :esse-id :program-info)) collected)]
       (insert! ::world/global ::shader/all all-shaders))]}))

(def system
  {::world/init-fn init-fn
   ::world/rules rules})