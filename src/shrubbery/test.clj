(ns shrubbery.test
  "An extension to `clojure.test`'s `is` macro for working with mocks."
  (require [clojure.test :refer [assert-expr do-report]]
           [shrubbery.core :refer :all]))

(defmethod assert-expr 'received? [msg form]
  `(let [spy# ~(nth form 1)
         method# ~(-> (nth form 2) (str) (keyword))
         args# ~(some-> form (nth 3 nil))]
     (let [count# (apply call-count spy# (remove nil? [method# args#]))
           result# (= count# 1)]
       (if result#
         (do-report {:type :pass :message ~msg :expected '~form :actual (method# (calls spy#))})
         (do-report {:type :fail :message ~msg :expected '~form :actual (format "received %s times; other calls: %s" count# (or (method# (calls spy#)) []))})
       ))))
