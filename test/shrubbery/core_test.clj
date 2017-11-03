(ns shrubbery.core-test
  (:require [clojure.test :refer :all]
            [shrubbery.core :refer :all]
            [shrubbery.clojure.test :refer :all]
            [shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases :as use-cases])
  (:import (java.util ArrayList)))

(defprotocol AProtocol
  (foo  [this])
  (bar  [this that])
  (baz  [this that the-other-thing])
  (quux [this] [this that]))

(def proto
  (reify AProtocol
    (foo [_] :hello-foo)
    (bar [_ _] :hello-bar)
    (baz [_ _ _] :hello-baz)
    (quux [_] :hello-quux-1)
    (quux [_ _] :hello-quux-2)))

(deftest test-spy
  (testing "with illegal arguments"
    (is (thrown? IllegalArgumentException (spy (Object.))))
    (is (thrown? IllegalArgumentException (spy proto []))))

  (testing "that it's an instance"
    (is (spy? (spy proto)))
    (is (not (spy? proto)))
    (is (not (spy? (Object.)))))

  (testing "a simple call counter"
    (let [subject (spy proto)]
      (is (= 0 (call-count subject foo)))
      (is (= :hello-foo (foo subject)))
      (is (= 1 (call-count subject foo)))
      (is (= :hello-foo (foo subject)))
      (is (= 2 (call-count subject foo)))))

  (testing "correct proxy behavior"
    (let [subject (spy proto)]
      (is (= :hello-foo (foo subject)))
      (is (= :hello-bar (bar subject nil)))
      (is (= :hello-baz (baz subject nil nil)))
      (is (= :hello-quux-1 (quux subject)))
      (is (= :hello-quux-2 (quux subject nil)))))


  (testing "a call counter with simple argument equality"
    (let [subject (spy proto)]
      (is (= 0 (call-count subject bar)))

      (bar subject "yes")
      (is (= 0 (call-count subject bar ["no"])))
      (is (= 1 (call-count subject bar ["yes"])))

      (bar subject :symbol)
      (is (= 1 (call-count subject bar [:symbol])))

      (bar subject (doto (ArrayList.) (.add 1)))
      (is (= 1 (call-count subject bar [(doto (ArrayList.) (.add 1))])))))

  (testing "a call counter with regexp matching"
    (let [subject (spy proto)]
      (is (= 0 (call-count subject bar)))

      (bar subject "yes")
      (is (= 0 (call-count subject bar ["no"])))
      (is (= 0 (call-count subject bar [#"no"])))
      (is (= 1 (call-count subject bar [#"yes"])))
      (is (= 1 (call-count subject bar [#"y.."])))

      (bar subject "yess")
      (is (= 1 (call-count subject bar ["yes"])))
      (is (= 1 (call-count subject bar [#"yess"])))
      (is (= 2 (call-count subject bar [#"yes"])))
      (is (= 2 (call-count subject bar [#"y*"])))))

  (testing "a call counter that matches anything"
    (let [subject (spy proto)]
      (is (= 0 (call-count subject bar)))

      (bar subject "wow such matching")
      (is (= 1 (call-count subject bar)))
      (is (= 1 (call-count subject bar [anything])))))

  (testing "a call counter that matches multiple arguments"
    (let [subject (spy proto)]
      (is (= 0 (call-count subject baz)))

      (baz subject "hello" "world")
      (is (= 1 (call-count subject baz ["hello" "world"])))
      (is (= 0 (call-count subject baz ["hello" "w"])))
      (is (= 1 (call-count subject baz ["hello" anything])))))

  (testing "a call counter with arbitrary matching"
    (let [subject (spy proto)]
      (is (= 0 (call-count subject baz)))

      (bar subject 2)
      (is (= 1 (call-count subject bar [#(> % 1)])))
      (is (= 0 (call-count subject bar [#(> % 2)])))
      (bar subject 1)
      (is (= 2 (call-count subject bar [#(> % 0)])))
      (is (= 1 (call-count subject bar [#(> % 1)])))))

  (testing "a protocol in a different namespace"
    (let [impl (reify shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/Clearly (duh [t] :uhdoy))
          subject (spy impl)]
      (is (= 0 (call-count subject use-cases/duh)))
      (is (= :uhdoy (shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/duh subject)))
      (is (= 1 (call-count subject use-cases/duh)))))

  (testing "an implementation with multiple protocols across namespaces"
    (let [impl (reify
                 use-cases/Clearly
                 (duh [_] :uhdoy)
                 AProtocol
                 (foo [_] :foo)
                 (bar [_ _] :bar)
                 (baz [_ _ _] :baz))
          subject (spy impl)]
      (is (= 0 (call-count subject use-cases/duh)))
      (is (= :uhdoy (use-cases/duh subject)))
      (is (= 1 (call-count subject use-cases/duh)))

      (is (= 0 (call-count subject foo)))
      (is (= :foo (foo subject)))
      (is (= 1 (call-count subject foo)))

      (is (= 0 (call-count subject bar)))
      (is (= :bar (bar subject nil)))
      (is (= 1 (call-count subject bar)))

      (is (= 0 (call-count subject baz)))
      (is (= :baz (baz subject nil nil)))
      (is (= 1 (call-count subject baz)))))

  (testing "a call counter with a single supplied argument that isn't a vector"
    (let [subject (spy proto)]
      (is (thrown? IllegalArgumentException
                   (call-count subject bar "hello")))
      (is (thrown? IllegalArgumentException
                   (call-count subject bar :foo))))))

(deftest test-received?
  (testing "a simple received? call"
    (let [subject (spy proto)]
      (foo subject)
      (is (received? subject foo))
      (is (not (received? subject bar)))

      (bar subject "hello")
      (is (received? subject bar))
      (is (received? subject bar ["hello"]))
      (is (not (received? subject bar ["nonsense"]))))))

(deftest test-stub
  (testing "that it's an instance"
    (is (stub? (stub AProtocol)))
    (is (not (stub? proto)))
    (is (not (stub? (Object.)))))

  (testing "with illegal arguments"
    (is (thrown? IllegalArgumentException (stub)))
    (is (thrown? IllegalArgumentException (stub {})))
    (is (thrown? IllegalArgumentException (stub Spy AProtocol Object))))

  (testing "with no provided implementations"
    (let [subject (stub AProtocol)]
      (is (nil? (foo subject)))
      (is (nil? (bar subject :hello)))
      (is (nil? (baz subject :hello :world)))
      (is (nil? (quux subject)))
      (is (nil? (quux subject :hello)))))

  (testing "with an empty implementation"
    (let [subject (stub AProtocol {})]
      (is (nil? (foo subject)))
      (is (nil? (bar subject :hello)))
      (is (nil? (baz subject :hello :world)))
      (is (nil? (quux subject)))
      (is (nil? (quux subject :hello)))))

  (testing "with a stub impl that resolves to a function"
    (is (thrown? RuntimeException (stub AProtocol {:foo (fn [] :foo)}))))

  (testing "with an immediate simple primitive"
    (let [subject (stub AProtocol {:foo 1 :bar "two" :baz 'three :quux :four})]
      (is (= 1 (foo subject)))
      (is (= "two" (bar subject :hello)))
      (is (= 'three (baz subject :hello :world)))
      (is (= :four (quux subject)))
      (is (= :four (quux subject :hello)))))

  (testing "with a let-binding that resolves to a primitive"
    (let [some-o "some object"
          subject (stub AProtocol {:foo some-o :bar some-o :baz some-o :quux some-o})]
      (is (= "some object" (foo subject)))
      (is (= "some object" (bar subject :hello)))
      (is (= "some object" (baz subject :hello :world)))
      (is (= "some object" (quux subject)))
      (is (= "some object" (quux subject :hello)))))

  (testing "with a let-binding that resolves to the entirety of the stub"
    (let [stuff {:foo 1 :bar "two" :baz 'three :quux :four}
          subject (stub AProtocol stuff)]
      (is (= 1 (foo subject)))
      (is (= "two" (bar subject :hello)))
      (is (= 'three (baz subject :hello :world)))
      (is (= :four (quux subject)))
      (is (= :four (quux subject :hello)))))

  (testing "a protocol in a different namespace"
    (let [subject (stub shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/Clearly {:duh :uhdoy})]
      (is (= :uhdoy (shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/duh subject)))))

  (testing "multiple protocols across namespaces"
    (let [subject (stub use-cases/Clearly AProtocol {:foo "foo"})]
      (is (nil? (use-cases/duh subject)))
      (is (= "foo" (foo subject)))
      (is (nil? (bar subject nil)))))

  (testing "with an object wrapped using `throws`"
    (let [subject (stub AProtocol {:foo (throws RuntimeException)})]
      (is (thrown? RuntimeException (foo subject))))
    (let [subject (stub AProtocol {:foo (throws RuntimeException "foo")})]
      (is (thrown? RuntimeException (foo subject)))
      (is (thrown-with-msg? RuntimeException #"foo" (foo subject))))))


(deftest test-returning
  (testing "with no existing stubbed values"
    (let [subject (-> (stub AProtocol)
                      (returning AProtocol {:foo "bar"}))]
      (is (= "bar" (foo subject)))))

  (testing "'changing' an existing stubbed value"
    (let [subject (-> (stub AProtocol {:foo "foo"})
                      (returning AProtocol {:foo "foo2"}))]
      (is (= "foo2" (foo subject)))))

  (testing "existing implementations of the same protocol are unaltered"
    (let [subject (-> (stub AProtocol {:foo "foo" :bar "bar"})
                      (returning AProtocol {:foo "foo2"}))]
      (is (= "bar" (bar subject nil)))
      (is (= "foo2" (foo subject)))))

  (testing "'changing' multiple stubbed values at once"
    (let [subject (-> (stub AProtocol {:foo "foo" :bar "bar"})
                      (returning AProtocol {:foo "foo2" :bar "bar2"}))]
      (is (= "foo2" (foo subject)))
      (is (= "bar2" (bar subject nil)))))

  (testing "existing implementations of other protocols are unaltered"
    (let [subject (-> (stub AProtocol {:foo "foo"} use-cases/Clearly {:duh "duh"})
                      (returning AProtocol {:foo "foo2"}))]
      (is (= "duh" (use-cases/duh subject)))
      (is (= "foo2" (foo subject)))))

  (testing "with no prior implementations of the named protocol"
    (let [subject (-> (stub AProtocol)
                      (returning use-cases/Clearly {:duh "duh"}))]
      (is (= "duh" (use-cases/duh subject)))))

  (testing "with a mock rather than a stub"
    (let [subject (-> (mock AProtocol {:foo "foo"})
                      (returning AProtocol {:foo "bar"}))]
      (is (= "bar" (foo subject)))
      (is (spy? subject))
      (is (received? subject foo))))

  (testing "if given an object that is neither a stub or a mock"
    (is (thrown? IllegalArgumentException (returning (Object.))))))

(deftest test-mock
  (testing "with no provided implementations"
    (let [subject (mock AProtocol)]
      (is (nil? (foo subject)))
      (is (received? subject foo))))

  (testing "with an empty implementation"
    (let [subject (mock AProtocol {})]
      (is (nil? (foo subject)))
      (is (received? subject foo))))

  (testing "with a basic implementation"
    (let [subject (mock AProtocol {:bar 5})]
      (is (= 5 (bar subject "wow")))
      (is (received? subject bar))
      (is (not (received? subject foo)))
      (is (received? subject bar ["wow"]))
      (is (not (received? subject bar ["woo"]))))))


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
      (is (= #{shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/Clearly} (protocols subject))))))


(deftest test-reify-syntax-for-stub
  (testing "with a symbol"
    (is (= '(quote foo) (reify-syntax-for-stub 'foo))))
  (testing "with a function"
    (is (thrown? RuntimeException (reify-syntax-for-stub (fn [] "hello world")))))
  (testing "with an immediate value"
    (is (= '"foo" (reify-syntax-for-stub "foo")))
    (is (= '1 (reify-syntax-for-stub 1)))
    (is (= '{} (reify-syntax-for-stub {}))))
  (testing "with a Throwable"
    (is (= `(throw (new ~RuntimeException "foo"))
            (reify-syntax-for-stub (throws RuntimeException "foo"))))))

(deftest test-stub-record
  (testing "is an instance"
    (is (stub? (stub-record {} AProtocol))))

  (testing "is maplike"
    (is (= 1 (count (stub-record {} AProtocol))))
    (is (nil? (:foo (stub-record {} AProtocol))))
    (is (= "bar" (-> {} (stub-record AProtocol) (assoc :foo "bar") :foo)))
    (is (empty? (stub-record {} AProtocol)))))
