(ns shrubbery.core-test
  (:require [clojure.test :refer :all]
            [shrubbery.core :refer :all]
            [shrubbery.clojure.test :refer :all]
            [shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases :as use-cases]))

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
    (let [subject (spy proto AProtocol)]
      (is (= 0 (call-count subject :foo)))
      (is (= :hello-foo (foo subject)))
      (is (= 1 (call-count subject :foo)))
      (is (= :hello-foo (foo subject)))
      (is (= 2 (call-count subject :foo)))
      ))

  (testing "correct proxy behavior"
    (let [subject (spy proto AProtocol)]
      (is (= :hello-foo (foo subject)))
      (is (= :hello-bar (bar subject nil)))
      (is (= :hello-baz (baz subject nil nil)))
      ))

  (testing "a call counter with simple argument equality"
    (let [subject (spy proto AProtocol)]
      (is (= 0 (call-count subject :bar)))

      (bar subject "yes")
      (is (= 0 (call-count subject :bar ["no"])))
      (is (= 1 (call-count subject :bar ["yes"])))

      (bar subject :symbol)
      (is (= 1 (call-count subject :bar [:symbol])))
      ))

  (testing "a call counter with regexp matching"
    (let [subject (spy proto AProtocol)]
      (is (= 0 (call-count subject :bar)))

      (bar subject "yes")
      (is (= 0 (call-count subject :bar ["no"])))
      (is (= 0 (call-count subject :bar [#"no"])))
      (is (= 1 (call-count subject :bar [#"yes"])))
      (is (= 1 (call-count subject :bar [#"y.."])))

      (bar subject "yess")
      (is (= 1 (call-count subject :bar ["yes"])))
      (is (= 1 (call-count subject :bar [#"yess"])))
      (is (= 2 (call-count subject :bar [#"yes"])))
      (is (= 2 (call-count subject :bar [#"y*"])))
      ))

  (testing "a call counter that matches anything"
    (let [subject (spy proto AProtocol)]
      (is (= 0 (call-count subject :bar)))

      (bar subject "wow such matching")
      (is (= 1 (call-count subject :bar)))
      (is (= 1 (call-count subject :bar [anything])))
      ))

  (testing "a call counter that matches multiple arguments"
    (let [subject (spy proto AProtocol)]
      (is (= 0 (call-count subject :baz)))

      (baz subject "hello" "world")
      (is (= 1 (call-count subject :baz ["hello" "world"])))
      (is (= 0 (call-count subject :baz ["hello" "w"])))
      (is (= 1 (call-count subject :baz ["hello" anything])))
      ))

  (testing "a call counter with arbitrary matching"
    (let [subject (spy proto AProtocol)]
      (is (= 0 (call-count subject :baz)))

      (bar subject 2)
      (is (= 1 (call-count subject :bar [#(> % 1)])))
      (is (= 0 (call-count subject :bar [#(> % 2)])))
      (bar subject 1)
      (is (= 2 (call-count subject :bar [#(> % 0)])))
      (is (= 1 (call-count subject :bar [#(> % 1)])))
      ))

  (testing "a protocol in a different namespace"
    (let [impl (reify shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/Clearly (duh [t] :uhdoy))
          subject (spy impl shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/Clearly)]
      (is (= 0 (call-count subject :duh)))
      (is (= :uhdoy (shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/duh subject)))
      (is (= 1 (call-count subject :duh)))
      ))
  )

(deftest test-received?
  (testing "a simple received? call"
    (let [subject (spy proto AProtocol)]
      (foo subject)
      (is (received? subject foo))
      (is (not (received? subject bar)))

      (bar subject "hello")
      (is (received? subject bar))
      (is (received? subject bar ["hello"]))
      (is (not (received? subject bar ["nonsense"])))
      )))

(deftest test-stub
  (testing "with no provided implementations"
    (let [subject (stub AProtocol)]
      (is (nil? (foo subject)))
      (is (nil? (bar subject :hello)))
      (is (nil? (baz subject :hello :world)))
      ))

  (testing "with an empty implementation"
    (let [subject (stub AProtocol {})]
      (is (nil? (foo subject)))
      (is (nil? (bar subject :hello)))
      (is (nil? (baz subject :hello :world)))
      ))

  (comment
    (testing "with a let-binding that resolves to a function"
     (let [some-fn (fn [& args] :foo)
           subject (stub AProtocol {:foo some-fn :bar some-fn :baz some-fn})]
       (is (= :foo (foo subject)))
       (is (= :foo (bar subject :hello)))
       (is (= :foo (baz subject :hello :world)))
       )))

  (testing "with an immediate simple primitive"
    (let [subject (stub AProtocol {:foo 1 :bar "two" :baz 'three})]
      (is (= 1 (foo subject)))
      (is (= "two" (bar subject :hello)))
      (is (= 'three (baz subject :hello :world)))
      ))

  (testing "with a let-binding that resolves to a primitive"
    (let [some-o "some object"
          subject (stub AProtocol {:foo some-o :bar some-o :baz some-o})]
      (is (= "some object" (foo subject)))
      (is (= "some object" (bar subject :hello)))
      (is (= "some object" (baz subject :hello :world)))
      ))

  (testing "with a let-binding that resolves to the entirety of the stub"
    (let [stuff {:foo 1 :bar "two" :baz 'three}
          subject (stub AProtocol stuff)]
      (is (= 1 (foo subject)))
      (is (= "two" (bar subject :hello)))
      (is (= 'three (baz subject :hello :world)))
      ))

  (testing "a protocol in a different namespace"
    (let [subject (stub shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/Clearly {:duh :uhdoy})]
      (is (= :uhdoy (shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/duh subject)))))
 )

(deftest test-mock
  (testing "with no provided implementations"
    (let [subject (mock AProtocol)]
      (is (nil? (foo subject)))
      (is (received? subject foo))
      ))

  (testing "with an empty implementation"
    (let [subject (mock AProtocol {})]
      (is (nil? (foo subject)))
      (is (received? subject foo))
      ))

  (testing "with a basic implementation"
    (let [subject (mock AProtocol {:bar 5})]
      (is (= 5 (bar subject "wow")))
      (is (received? subject bar))
      (is (not (received? subject foo)))
      (is (received? subject bar ["wow"]))
      (is (not (received? subject bar ["woo"])))
      ))
  )

(defrecord RecProtocol []
  AProtocol)

(deftest test-protocols
  (testing "with no protocols"
    (let [subject (Object.)]
      (is (= #{} (protocols subject)))))

  (testing "with a single protocol with no hyphens"
    (let [subject (reify shrubbery.core/Spy)]
      (is (= #{shrubbery.core/Spy} (protocols subject)))))

  (testing "with a single simple protocol with a hyphen"
    (let [subject (reify AProtocol)]
      (is (= #{shrubbery.core-test/AProtocol} (protocols subject)))))

  (testing "with multiple protocols from different namespaces"
    (let [subject (reify AProtocol shrubbery.core/Spy shrubbery.core/Stub)]
      (is (= #{AProtocol shrubbery.core/Spy shrubbery.core/Stub} (protocols subject)))))

  (testing "with a record that implements a protocol"
    (let [subject (->RecProtocol)]
      (is (= #{AProtocol} (protocols subject)))))

  (testing "with a non-local protocol"
    (let [subject (reify shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/Clearly)]
      (is (= #{shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/Clearly} (protocols subject)))))
  )
