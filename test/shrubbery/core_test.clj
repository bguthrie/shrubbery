(ns shrubbery.core-test
  (:require [clojure.test :refer :all]
            [shrubbery.core :refer :all]
            [shrubbery.clojure.test :refer :all]
<<<<<<< HEAD
            [shrubbery.proto-helper :as p]))
=======
            [shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases :as use-cases]))
>>>>>>> master

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
  (testing "with illegal arguments"
    (is (thrown? IllegalArgumentException (spy (Object.))))
    (is (thrown? IllegalArgumentException (spy proto []))))

  (testing "that it's an instance"
    (is (spy? (spy proto)))
    (is (not (spy? proto)))
    (is (not (spy? (Object.)))))

  (testing "a simple call counter"
<<<<<<< HEAD
    (let [subject (spy AProtocol proto)]
      (is (= 0 (call-count subject foo)))
=======
    (let [subject (spy proto)]
      (is (= 0 (call-count subject :foo)))
>>>>>>> master
      (is (= :hello-foo (foo subject)))
      (is (= 1 (call-count subject foo)))
      (is (= :hello-foo (foo subject)))
      (is (= 2 (call-count subject foo)))
      ))

  (testing "correct proxy behavior"
    (let [subject (spy proto)]
      (is (= :hello-foo (foo subject)))
      (is (= :hello-bar (bar subject nil)))
      (is (= :hello-baz (baz subject nil nil)))
      ))

  (testing "a call counter with simple argument equality"
<<<<<<< HEAD
    (let [subject (spy AProtocol proto)]
      (is (= 0 (call-count subject bar)))
=======
    (let [subject (spy proto)]
      (is (= 0 (call-count subject :bar)))
>>>>>>> master

      (bar subject "yes")
      (is (= 0 (call-count subject bar ["no"])))
      (is (= 1 (call-count subject bar ["yes"])))

      (bar subject :symbol)
      (is (= 1 (call-count subject bar [:symbol])))
      ))

  (testing "a call counter with regexp matching"
<<<<<<< HEAD
    (let [subject (spy AProtocol proto)]
      (is (= 0 (call-count subject bar)))
=======
    (let [subject (spy proto)]
      (is (= 0 (call-count subject :bar)))
>>>>>>> master

      (bar subject "yes")
      (is (= 0 (call-count subject bar ["no"])))
      (is (= 0 (call-count subject bar [#"no"])))
      (is (= 1 (call-count subject bar [#"yes"])))
      (is (= 1 (call-count subject bar [#"y.."])))

      (bar subject "yess")
      (is (= 1 (call-count subject bar ["yes"])))
      (is (= 1 (call-count subject bar [#"yess"])))
      (is (= 2 (call-count subject bar [#"yes"])))
      (is (= 2 (call-count subject bar [#"y*"])))
      ))

  (testing "a call counter that matches anything"
<<<<<<< HEAD
    (let [subject (spy AProtocol proto)]
      (is (= 0 (call-count subject bar)))
=======
    (let [subject (spy proto)]
      (is (= 0 (call-count subject :bar)))
>>>>>>> master

      (bar subject "wow such matching")
      (is (= 1 (call-count subject bar)))
      (is (= 1 (call-count subject bar [anything])))
      ))

  (testing "a call counter that matches multiple arguments"
<<<<<<< HEAD
    (let [subject (spy AProtocol proto)]
      (is (= 0 (call-count subject baz)))
=======
    (let [subject (spy proto)]
      (is (= 0 (call-count subject :baz)))
>>>>>>> master

      (baz subject "hello" "world")
      (is (= 1 (call-count subject baz ["hello" "world"])))
      (is (= 0 (call-count subject baz ["hello" "w"])))
      (is (= 1 (call-count subject baz ["hello" anything])))
      ))

  (testing "a call counter with arbitrary matching"
<<<<<<< HEAD
    (let [subject (spy AProtocol proto)]
      (is (= 0 (call-count subject baz)))
=======
    (let [subject (spy proto)]
      (is (= 0 (call-count subject :baz)))
>>>>>>> master

      (bar subject 2)
      (is (= 1 (call-count subject bar [#(> % 1)])))
      (is (= 0 (call-count subject bar [#(> % 2)])))
      (bar subject 1)
      (is (= 2 (call-count subject bar [#(> % 0)])))
      (is (= 1 (call-count subject bar [#(> % 1)])))
      ))

  (testing "a protocol in a different namespace"
    (let [impl (reify shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/Clearly (duh [t] :uhdoy))
          subject (spy impl)]
      (is (= 0 (call-count subject :duh)))
      (is (= :uhdoy (shrubbery.is.a.great.library.with.lots.of.super-obvious.use-cases/duh subject)))
      (is (= 1 (call-count subject :duh)))
      ))

  (testing "an implementation with multiple protocols across namespaces"
    (let [impl (reify
                 use-cases/Clearly
                 (duh [_] :uhdoy)
                 AProtocol
                 (foo [_] :foo)
                 (bar [_ _] :bar)
                 (baz [_ _ _] :baz))
          subject (spy impl)]
      (is (= 0 (call-count subject :duh)))
      (is (= :uhdoy (use-cases/duh subject)))
      (is (= 1 (call-count subject :duh)))

      (is (= 0 (call-count subject :foo)))
      (is (= :foo (foo subject)))
      (is (= 1 (call-count subject :foo)))

      (is (= 0 (call-count subject :bar)))
      (is (= :bar (bar subject nil)))
      (is (= 1 (call-count subject :bar)))

      (is (= 0 (call-count subject :baz)))
      (is (= :baz (baz subject nil nil)))
      (is (= 1 (call-count subject :baz)))
      ))
  )

(deftest test-received?
  (testing "a simple received? call"
    (let [subject (spy proto)]
      (foo subject)
      (is (received? subject foo))
      (is (not (received? subject bar)))

      (bar subject "hello")
      (is (received? subject bar))
      (is (received? subject bar ["hello"]))
      (is (not (received? subject bar ["nonsense"])))
      )))

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
      ))

  (testing "with an empty implementation"
    (let [subject (stub AProtocol {})]
      (is (nil? (foo subject)))
      (is (nil? (bar subject :hello)))
      (is (nil? (baz subject :hello :world)))
      ))

<<<<<<< HEAD
  (testing "with a let-binding that resolves to a function"
    (let [some-fn (fn [& args] :foo)
          subject (stub AProtocol {foo some-fn bar some-fn baz some-fn})]
      (is (= :foo (foo subject)))
      (is (= :foo (bar subject :hello)))
      (is (= :foo (baz subject :hello :world)))
      ))
=======
  (comment
    (testing "with a let-binding that resolves to a function"
     (let [some-fn (fn [& args] :foo)
           subject (stub AProtocol {:foo some-fn :bar some-fn :baz some-fn})]
       (is (= :foo (foo subject)))
       (is (= :foo (bar subject :hello)))
       (is (= :foo (baz subject :hello :world)))
       )))

  (testing "with a stub impl that resolves to a function"
    (is (thrown? RuntimeException (stub AProtocol {:foo (fn [] :foo)}))))
>>>>>>> master

  (testing "with an immediate simple primitive"
    (let [subject (stub AProtocol {foo 1 bar "two" baz 'three})]
      (is (= 1 (foo subject)))
      (is (= "two" (bar subject :hello)))
      (is (= 'three (baz subject :hello :world)))
      ))

  (testing "with a let-binding that resolves to a primitive"
    (let [some-o "some object"
          subject (stub AProtocol {foo some-o bar some-o baz some-o})]
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

  (testing "multiple protocols across namespaces"
    (let [subject (stub use-cases/Clearly AProtocol {:foo "foo"})]
      (is (nil? (use-cases/duh subject)))
      (is (= "foo" (foo subject)))
      (is (nil? (bar subject nil)))
      ))
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
<<<<<<< HEAD
    (let [subject (mock AProtocol {bar (fn [this that] that)})]
      (is (= "wow" (bar subject "wow")))
=======
    (let [subject (mock AProtocol {:bar 5})]
      (is (= 5 (bar subject "wow")))
>>>>>>> master
      (is (received? subject bar))
      (is (not (received? subject foo)))
      (is (received? subject bar ["wow"]))
      (is (not (received? subject bar ["woo"])))
      ))
  )

<<<<<<< HEAD
(deftest test-namespaced-spy
  (testing "a simple call counter"
    (let [subject (spy p/NamespacedProto
                       (reify p/NamespacedProto
                         (zzz [_])))]
      (is (= 0 (call-count subject p/zzz)))
      (p/zzz subject)
      (is (= 1 (call-count subject p/zzz)))
      (p/zzz subject)
      (is (= 2 (call-count subject p/zzz)))
      ))
  )

(deftest test-namespaced-mock
  (testing "with no provided implementations"
    (let [subject (mock p/NamespacedProto)]
      (is (nil? (p/zzz subject)))
      (is (received? subject p/zzz))
      ))

  (testing "with an empty implementation"
    (let [subject (mock p/NamespacedProto {})]
      (is (nil? (p/zzz subject)))
      (is (received? subject p/zzz))
      ))

  (testing "with a basic implementation"
    (let [subject (mock p/NamespacedProto {p/zzz-arged (fn [this that] that)})]
      (is (= "wow" (p/zzz-arged subject "wow")))
      (is (received? subject p/zzz-arged))
      (is (not (received? subject p/zzz)))
      (is (received? subject shrubbery.proto-helper/zzz-arged ["wow"]))
      (is (not (received? subject p/zzz-arged ["woo"])))
      ))
  )
=======
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
>>>>>>> master
