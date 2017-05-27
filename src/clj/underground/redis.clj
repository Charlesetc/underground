(ns underground.redis
  (:require [taoensso.carmine :as car]))

(def server1-conn {:pool {} :spec {}}) ; See `wcar` docstring for opts

(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

(defn set [k v]
  (wcar* (car/set k v)))

(defn get [k]
  (wcar* (car/get k)))
