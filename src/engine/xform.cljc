(ns engine.xform)

(defn accumulate-time [rf]
  (let [acc-time! (volatile! 0)]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result [curr & rest']]
       (let [time'  (+ curr @acc-time!)
             input' (apply vector time' rest')]
         (vreset! acc-time! time')
         (rf result input'))))))