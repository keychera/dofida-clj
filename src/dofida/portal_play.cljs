(ns dofida.portal-play)

(defn msec->sec [n] (* 0.001 n))

(defn update-loop! [game cancel-atom]
  (js/requestAnimationFrame
   (fn [ts]
     (tap> (:time game))
     (when (not @cancel-atom)
       (let [ts (msec->sec ts)]
         (update-loop! (assoc game :time ts) cancel-atom))))))


(defn start! []
  (let [cancel-atom (atom false)]
    (update-loop! {} cancel-atom)
    #(swap! cancel-atom not)))

(comment
  (def cancel (start!))
  (cancel)

  (add-tap #(console.log %))

  (console.log "hello")

  ;;  weird require error
  ;; (require '[portal.web :as portalweb])
  ;; (def a (portalweb/open))

  (require 'portal.web)
  (def a (portal.web/open))

  (add-tap #'portal.web/submit)
  (def hello (atom {:say "hello" :value 1}))
  (tap> hello)
  
  (swap! hello update :value inc)
  
  (portal.web/clear)
  (remove-tap #'portal.web/submit)

  (portal.web/close))