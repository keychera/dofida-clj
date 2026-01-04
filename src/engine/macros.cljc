(ns engine.macros
  #?@(:clj [(:require
             [clojure.java.io :as io]
             [odoyle.rules :as o])
            (:import
             [java.nio.file Paths])])
  #?(:cljs (:require-macros [engine.macros :refer [s-> insert! vars->map]])))

#?(:clj
   (defmacro s->
     "Thread like `->` but always ends with (o/reset!)."
     [x & forms]
     `(-> ~x ~@forms o/reset!)))

#?(:clj
   (defmacro insert!
     "this is the same as `o/insert!`. 
      the reason this macro exist is because I wanted to try nivekuil's branch of odoyle rules but
      that has a breaking change of not having `o/reset` so I wanted my codebase to be ready to receive such change by having this macro.
      This was a while ago. This is still using vanilla odoyle rules"
     ([[id attr value]]
      `(s-> ~'session (o/insert ~id ~attr ~value)))
     ([id attr->value]
      `(s-> ~'session (o/insert ~id ~attr->value)))
     ([id attr value]
      `(s-> ~'session (o/insert ~id ~attr ~value)))))

#?(:clj
   (defmacro vars->map [& vars]
     (zipmap (map (comp keyword name) vars) vars)))

;; this is loading resource on compile-time, hence cljs can have access to java stuff
#?(:clj (def public-resource-path (Paths/get (.toURI (io/resource "public")))))