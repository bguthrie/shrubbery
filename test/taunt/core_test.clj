(ns taunt.core-test
  (:require [clojure.test :refer :all]
            [taunt.core :refer :all]))

(defprotocol AProtocol
  (foo [this])
  (bar [this that])
  (baz [this that the-other-thing]))

(def proto
  (reify AProtocol
    (foo [_] :hello-foo)
    (bar [_ _] :hello-bar)
    (baz [_ _ _] :hello-baz)))

(deftest test-spy
  (testing "a simple call counter"
    (let [subject (spy AProtocol proto)]
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
    (let [subject (spy AProtocol proto)]
      (is (= 0
             (call-count subject :bar)))
      (is (= :hello-bar
             (bar subject "yes")))
      (is (= 0
             (call-count subject :bar ["no"])))
      (is (= 1
             (call-count subject :bar ["yes"])))
    ))

  (testing "a call counter with regexp matching"
    (let [subject (spy AProtocol proto)]
      (is (= 0
             (call-count subject :bar)))

      (bar subject "yes")
      (is (= 0
             (call-count subject :bar ["no"])))
      (is (= 0
             (call-count subject :bar [#"no"])))
      (is (= 1
             (call-count subject :bar [#"yes"])))
      (is (= 1
             (call-count subject :bar [#"y.."])))

      (bar subject "yess")
      (is (= 1
             (call-count subject :bar ["yes"])))
      (is (= 1
             (call-count subject :bar [#"yess"])))
      (is (= 2
             (call-count subject :bar [#"yes"])))
      (is (= 2
             (call-count subject :bar [#"y*"])))

    ))

  (testing "a call counter that matches anything"
    (let [subject (spy AProtocol proto)]
      (is (= 0
             (call-count subject :bar)))
      (bar subject "wow such matching")
      (is (= 1
             (call-count subject :bar)))
      (is (= 1
             (call-count subject :bar [anything])))
      ))

  (testing "a call counter that matches multiple arguments"
    (let [subject (spy AProtocol proto)]
      (is (= 0
             (call-count subject :baz)))
      (baz subject "hello" "world")
      (is (= 1
             (call-count subject :baz ["hello" "world"])))
      (is (= 0
             (call-count subject :baz ["hello" "w"])))
      (is (= 1
             (call-count subject :baz ["hello" anything])))
      ))

  (testing "a call counter with arbitrary matching")
  )
