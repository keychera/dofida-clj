(ns engine.macros
  (:require
   [odoyle.rules :as o]))

(defmacro s->
  "Thread like `->` but always ends with (o/reset!)."
  [x & forms]
  `(-> ~x ~@forms o/reset!))

(defmacro vars->map [& vars]
  (zipmap (map (comp keyword name) vars) vars))
