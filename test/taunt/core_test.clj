(ns taunt.core-test
  (:require [clojure.test :refer :all]
            [taunt.core :refer :all]))

(defprotocol AProtocol
  (foo [this])
  (bar [this that]))

(deftest test-spying-proxy
  (testing "a simple call counter"
    (let [proto (reify AProtocol (foo [_] :hello-foo) (bar [_ _] :hello-bar))
          subject (spying-proxy AProtocol proto)]
      (is (= 0 (call-count subject :foo) 0))
      (is (= :hello-foo (foo subject)))
      (is (= 1 (call-count subject :foo)))
      (is (= :hello-foo (foo subject)))
      (is (= 2 (call-count subject :foo)))
      )))
