(ns shrubbery.core
  "A collection of functions for creating and querying stubs, spies, and mocks."
  (:require [clojure.string :as str])
  (:import (clojure.lang IPersistentCollection Associative ILookup)))

(defprotocol Spy
  "A protocol shared by spies."
  (calls [t]
    "Returns a map of function names to lists of received args, one for each time the function is called."))

(defprotocol Proxy
  "A protocol shared by protocol proxies."
  (proxied [t]
    "Returns the underlying object this spy proxies its calls to."))

(defprotocol Stub
  "A protocol shared by stubs."
  (all-stubs [t]
    "Returns a normalized hashmap of all known stubs prior to reification."))

(defprotocol Matcher
  "A protocol for defining argument equality matching. Default implementations are provided for `Object` (equality),
  `Pattern` (regexp matching), and `IFn` (execution). There is also a wildcard matcher, [[anything]]."
  (matches? [matcher value]))

(defn protocol?
  "True if p is a protocol."
  [p]
  (boolean (:on-interface p)))

(defn reifies?
  "True if o reifies the given protocol."
  [o proto]
  (-> proto :on-interface (instance? o)))

(defn stub?
  "True if s reifies Stub."
  [s]
  (reifies? s Stub))

(defn spy?
  "True if s reifies Spy."
  [s]
  (reifies? s Spy))

(defn proxy?
  "True if p reifies Proxy."
  [s]
  (reifies? s Proxy))

(defn- find-proto-var
  "Attempts to find the protocol var representing the given class, or nil if none found."
  [^Class maybe-proto]
  (let [pkg-name   (->> (.getName maybe-proto) (re-find #"^(.*?)\.[^\.]+$") (second))
        ns-name    (str/replace pkg-name "_" "-")
        proto-name (.getSimpleName maybe-proto)]
    (when (find-ns (symbol ns-name))
      (find-var (symbol ns-name proto-name)))))

(defn protocols
  "Given an object, attempt to return the complete set of protocols it reifies."
  [o]
  (->> (supers (class o))
       (map find-proto-var)
       (remove nil?)
       (map var-get)
       (set)))

(defn unique-list-of-all-sig-names-and-arglists [proto]
  (reduce
    (fn [coll [nm sig]]
      (concat coll
              (map (fn [lst] [(:name sig) lst]) (:arglists sig))))
    []
    (:sigs proto)))

(defn- proxy-syntax-for-single-proto [proto syntax-generator-fn]
  (let [all-unique-sigs (unique-list-of-all-sig-names-and-arglists proto)
        mimpls (map syntax-generator-fn all-unique-sigs)]
    `(~(:on proto)
       ~@mimpls)))

(defn ^:no-doc increment-args [counts-state m & args]
  (assoc counts-state m (conj (counts-state m) args)))

(defn- syntax-for-spy-proxy-fn
  [atom-sym proxy-sym proto [sig-name sig-arglist]]
  (let [f-ref (symbol (-> proto :var meta :ns str) (str sig-name))]
    `(~f-ref ~sig-arglist                                           ; (some.ns/foo [this a b c]
       (swap! ~atom-sym increment-args ~f-ref ~@(rest sig-arglist)) ;   (swap! counts increment-args #'some.ns/foo a b c)
       (~f-ref ~proxy-sym ~@(rest sig-arglist)))))                  ;   (some.ns/foo proto-impl a b))

(defn- syntax-for-proxy-fn
  [proxy-sym proto [sig-name sig-arglist]]
  (let [f-ref (symbol (-> proto :var meta :ns str) (str sig-name))]
    `(~f-ref ~sig-arglist                                           ; (some.ns/foo [this a b c]
       (~f-ref ~proxy-sym ~@(rest sig-arglist)))))                  ;   (some.ns/foo proto-impl a b))

(defn- proto-spy-reify-syntax [atom-sym proxy-sym proto]
  (proxy-syntax-for-single-proto proto (partial syntax-for-spy-proxy-fn atom-sym proxy-sym proto)))

(declare ^:dynamic ^:no-doc *proxy*)

(defn spy
  "Given an object implementing one or more protocols, returns a new object that records each call to underlying
  protocol functions, then proxies to the original implementation. The returned object also implements `Spy`, which
  exposes those calls, and `Proxy`, which exposes the proxied object. If an explicit list of protocols is not given,
  the list will be inferred from the given object. For spies with added implementation, see [[mock]]. For queryies
  against spies, see [[call-count]] and [[received?]]."
  ([o]
   (spy o (protocols o)))
  ([o protos]
   (when (empty? protos)
     (throw (IllegalArgumentException. "No spyable protocols available for given object.")))

   (let [atom-sym (gensym "counts")
         proxy-sym (gensym "proxy")
         spy-syntax `(Spy (calls [t#] (deref ~atom-sym)))
         proxy-syntax `(Proxy (proxied [t#] ~proxy-sym))
         all-protos-syntax (map (partial proto-spy-reify-syntax atom-sym proxy-sym) protos)
         everything `(let [~atom-sym (atom {})
                           ~proxy-sym *proxy*]
                       (reify
                         ~@(reduce concat (conj all-protos-syntax spy-syntax proxy-syntax))))]
     (binding [*proxy* o]
       (eval everything)))))

(defprotocol ^:no-doc Stubbable
  (reify-syntax-for-stub [thing]))

(extend-protocol Stubbable
  clojure.lang.AFunction
  (reify-syntax-for-stub [_] (throw (RuntimeException. "Fns not supported in stub implementations")))
  clojure.lang.Symbol
  (reify-syntax-for-stub [thing] `'~thing)
  java.lang.Object
  (reify-syntax-for-stub [thing] thing))

(defn- syntax-for-stub-fn [proto impl-hash [sig-name sig-arglist]]
  (let [proto-ns          (-> proto :var meta :ns)
        f-ref             (symbol (str proto-ns) (str sig-name))
        stub-impl-or-nil  (get impl-hash (keyword sig-name))
        stub-value        (when-not (nil? stub-impl-or-nil) (reify-syntax-for-stub stub-impl-or-nil))]
    `(~f-ref ~sig-arglist ~stub-value)))

(defn- stub-reify-syntax [[proto impls]]
  (proxy-syntax-for-single-proto proto (partial syntax-for-stub-fn proto impls)))

(defn- impl? [maybe-impl]
  (and (not (protocol? maybe-impl)) (map? maybe-impl)))

(defn- expand-proto-stubs
  ([protos-and-impls]
   (expand-proto-stubs protos-and-impls []))
  ([protos-and-impls retval]
   (let [proto (first protos-and-impls)
         maybe-impl (first (rest protos-and-impls))
         [impl balance] (if (impl? maybe-impl)
                          [maybe-impl (rest (rest protos-and-impls))]
                          [{}         (rest protos-and-impls)])]
     (cond
       (nil? proto) retval
       (not (protocol? proto)) (throw (IllegalArgumentException. (str "Not a Clojure protocol: " proto)))
       :else (recur balance (conj retval [proto impl]))))))

(declare ^:dynamic ^:no-doc *stubs*)


(defn stub
  "Given a variable number of protocols, each followed by an optional hashmap of simple implementations, returns a new
  object implementing those protocols. Where no function implementation is provided, calls to that protocol function
  will return `nil` rather than raising `IllegalArgumentException`. Functions as values are not supported, as they
  require side effects to define; for more complex stubs, prefer using `reify`. For stubs with call count recording,
  use [[mock]]."
  [& protos-and-impls]
  (when (or (empty? protos-and-impls) (not (protocol? (first protos-and-impls))))
    (throw (IllegalArgumentException. (str "Must provide at least one protocol to stub; given " protos-and-impls))))

  (let [new-protos-and-impls (expand-proto-stubs protos-and-impls)
        stub-impls (map stub-reify-syntax new-protos-and-impls)
        stubs-sym (gensym "stubs")
        all-protos-syntax (conj stub-impls `(Stub (all-stubs [_] ~stubs-sym)))
        everything `(let [~stubs-sym *stubs*]
                      (reify ~@(reduce concat all-protos-syntax)))]

    (binding [*stubs* new-protos-and-impls]
      (eval everything))))

(defn stub-record
  "Given a variable number of protocols, each followed by an optional hashmap of simple implementations, returns a new
  object implementing those protocols. The returned object additionally behaves like a record. For usage, see [[stub]]."
  [record & protos-and-impls]

  (when (or (empty? protos-and-impls) (not (protocol? (first protos-and-impls))))
    (throw (IllegalArgumentException. (str "Must provide at least one protocol to stub; given " protos-and-impls))))

  (let [new-protos-and-impls (expand-proto-stubs protos-and-impls)
        stub-impls (map stub-reify-syntax new-protos-and-impls)
        stubs-sym (gensym "stubs")
        proxy-sym (gensym "proxy")
        stub-syntax `(Stub (all-stubs [_] ~stubs-sym))
        persistent-coll-syntax `(clojure.lang.IPersistentCollection
                                  ;(~'cons [this# obj#]
                                  ;  (stub-record (apply stub (.cons ~proxy-sym obj#) ~stubs-sym)))
                                  (~'count [this#]
                                    (.count ~proxy-sym))
                                  (~'empty [this#]
                                    (.empty ~proxy-sym))
                                  (~'equiv [this# obj#]
                                    (.equiv ~proxy-sym obj#)))
        associative-syntax `(clojure.lang.Associative
                              (~'assoc [this# k# v#]
                                (apply stub-record (cons (.assoc ~proxy-sym k# v#) (first ~stubs-sym))))
                              (~'containsKey [this# k#]
                                (.containsKey ~proxy-sym k#))
                              (~'entryAt [this# v#]
                                (println "finding" v#)
                                (.entryAt ~proxy-sym v#)))
        lookup-syntax `(clojure.lang.ILookup
                         (~'valAt [this# key#]
                           (.valAt ~proxy-sym key#))
                         (~'valAt [this# key# not-found#]
                           (.valAt ~proxy-sym key# not-found#)))
        seqable-syntax `(clojure.lang.Seqable
                          (~'seq [this#]
                            (.seq ~proxy-sym)))
        seq-syntax `(clojure.lang.ISeq
                      (~'first [this#]
                        (.first ~proxy-sym))
                      (~'next [this#]
                        (.next ~proxy-sym))
                      (~'more [this#]
                        (.more ~proxy-sym))
                      (~'cons [this# obj#]
                        (apply stub-record (cons (.cons ~proxy-sym obj#) (first ~stubs-sym)))))
        all-protos-syntax (conj stub-impls (concat stub-syntax persistent-coll-syntax associative-syntax lookup-syntax seq-syntax seqable-syntax))
        everything `(let [~stubs-sym *stubs*
                          ~proxy-sym *proxy*]
                      (reify
                        ~@(reduce concat all-protos-syntax)))]
    (binding [*stubs* new-protos-and-impls
              *proxy* record]
      (println everything)
      (eval everything))))

(defn mock
  "Given a protocol and a hashmap of function implementations, returns a new implementation of that protocol with those
  implementations. The returned implementation is also a spy, allowing you to inspect and assert against its calls.
  See [[spy]] and [[stub]]."
  [& protos-and-impls]
  (spy
    (apply stub protos-and-impls)))


(defn call-count
  "Given a [[spy]], a var, and an optional vector of args, return the number of times the spy received
  the method. If given args, filters the list of calls by matching the given args. Matched args may implement the
  [[Matcher]] protocol; the default implementation for `Object` is `=`."
  ([aspy avar]
   (-> (calls aspy) (get avar) (count)))
  ([aspy avar args]
   (when-not (vector? args)
     (throw (IllegalArgumentException. "Please supply a vector of arguments to check against this spy.")))
   (->>
     (get (calls aspy) avar)
     (filter #(matches? args %))
     (count))))

(defn received?
  "Given a [[spy]] and a method reference, return true if the method has been called on the spy at least once. If given
  args, filters the list of calls by matching the given args. Matched args may implement the [[Matcher]] protocol;
  the default implementation for `Object` is `=`."
  ([aspy avar]
   (>= (call-count aspy avar) 1))
  ([aspy avar args]
   (>= (call-count aspy avar args) 1)))

(defn returning
  "Given a [[stub]] or [[mock]], a protocol, and a hashmap of implementations, return a new stub that replaces the stubs
  for the named function with the given stubs."
  [s proto new-stubs]
  (if-not (stub? s)
    (throw (IllegalArgumentException. (str "Must provide a mock or a stub; given " s))))

  (let [all-stubs (apply hash-map (-> s all-stubs flatten))
        revised-proto-stub (-> all-stubs (get proto) (merge new-stubs))
        new-all-stubs (-> all-stubs (assoc proto revised-proto-stub) vec flatten)]
    (if (spy? s)
      (apply mock new-all-stubs)
      (apply stub new-all-stubs))))

(defn throws
  "Given a class and a list of constructor args, returns an object that, when used as part of a [[stub]] construct, will
  create and then throw a Throwable of the given type, constructed with the given args."
  [^Class throwable-class & args]
  (reify Stubbable
    (reify-syntax-for-stub [_] `(throw (new ~throwable-class ~@args)))))

(extend-protocol Matcher
  clojure.lang.AFunction
  (matches? [matcher value]
    (matcher value))
  java.util.regex.Pattern
  (matches? [matcher value]
    (-> matcher
        (re-seq value)
        (first)
        (boolean)))
  clojure.lang.IPersistentVector
  (matches? [matcher value]
    (->> (map matches? matcher value)
         (every? identity)))
  java.lang.Object
  (matches? [matcher value]
    (= matcher value)))


(def anything
  "A [[Matcher]] that always returns true."
  (reify Matcher
    (matches? [_ _] true)))
