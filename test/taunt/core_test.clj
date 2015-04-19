(ns taunt.core-test
  (:require [clojure.test :refer :all]
            [taunt.core :refer :all]))

(defprotocol AProtocol
  (foo [this])
  (bar [this that]))

(deftest test-spy
  (testing "a simple call counter"
    (let [proto (reify AProtocol (foo [_] :hello-foo) (bar [_ _] :hello-bar))
          subject (spy AProtocol proto)]
      (is (= 0
             (call-count subject :foo)))
      (is (= :hello-foo
             (foo subject)))
      (is (= 1
             (call-count subject :foo)))
      (is (= :hello-foo
             (foo subject)))
      (is (= 2
             (call-count subject :foo)))
      ))

  (testing "a call counter with simple argument equality"
    (let [proto (reify AProtocol (foo [_] :hello-foo) (bar [_ _] :hello-bar))
          subject (spy AProtocol proto)]
      (is (= 0
             (call-count subject :bar)))
      (is (= :hello-bar
             (bar subject "yes")))
      (is (= 0
             (call-count subject :bar ["no"])))
      (is (= 1
             (call-count subject :bar ["yes"])))
    ))

  (testing "a call counter with regexp matching")
  (testing "a call counter with arbitrary matching")
  )
