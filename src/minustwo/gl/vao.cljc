(ns minustwo.gl.vao
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [engine.world :as world]))

(s/def ::db* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))

(defn init-fn [world _game]
  (o/insert world ::world/global ::db* (atom {})))

(def system {::world/init-fn init-fn})