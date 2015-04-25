(ns shrubbery.core
  (require [clojure.repl :refer [pst]]))

(defprotocol Spy
  "A protocol for objects that expose call counts. `calls` should return a map of function names to lists of received
  args, one for each time the function is called."
  (calls [t]))

(defprotocol Matcher
  "A protocol for defining argument equality matching. Default implementations are provided for `Object` (equality),
  `Pattern` (regexp matching), and `IFn` (execution)."
  (matches? [matcher value]))

(extend-protocol Matcher
  clojure.lang.Fn
  (matches? [matcher value]
    (matcher value))
  java.util.regex.Pattern
  (matches? [matcher value]
    (-> matcher
        (re-seq value)
        (first)
        (boolean)))
  clojure.lang.ArraySeq
  (matches? [matcher value]
    (->> (map matches? value matcher)
         (every? identity)))
  java.lang.Object
  (matches? [matcher value]
    (= matcher value))
  )

(def anything
  "A Matcher that always returns true."
  (reify Matcher
    (matches? [_ _] true)))

(defn call-count
  "Given a spy, a keyword method name, and an optional vector of args, return the number of times the spy received
  the method. If given args, filters the list of calls by matching the given args. Matched args may implement the `Matcher`
  protocol; the default implementation for `Object` is `=`."
  ([spy method]
   (-> spy calls method count))
  ([spy method args]
  (->>
     (-> spy calls method)
     (filter #(matches? % args))
     (count))))

(defn received?
  ([spy method] (>= (call-count spy method) 1))
  ([spy method args] (>= (call-count spy method args) 1)))

(defn- fn-sigs [proto]
  (-> proto resolve var-get :sigs))

(defn proto-fn-with-proxy
  "Given a protocol implementation, a function with side effects, and a protcol function signature, return
  a syntax-quoted protocol method implementation that calls the function, then proxies to the given implementation."
  [impl f [m sig]]
  (let [f-sym (-> m name symbol)
        args (-> sig :arglists first)]
    `(~f-sym ~args                   ; (foo [this a b]
       (~f ~m ~@args)                ;   ((fn [method this a b] ...) :foo this a b)
       (~f-sym ~impl ~@(rest args))) ;   (foo proto-impl a b))
    ))

(defn proto-fn-with-impl
  "Given a function and a protocol function signature, return a syntax-quoted protocol method implementation that
  calls the function."
  [f [m sig]]
  (let [f-sym (-> m name symbol)
        args (-> sig :arglists first)]
    `(~f-sym ~args                   ; (foo [this a b]
       (~f ~@args))               ;   ((fn [method this a b] ...) :foo this a b)
    ))

(defmacro spy
  "Given a protocol and an implementation of that protocol, returns a new implementation of that protocol that counts
  the number of times each method was received. The returned implementation also implements `Spy`, which exposes those
  counts. Each method is proxied to the given impl after capture."
  [proto impl]
  (let [atom-sym (gensym "counts")
        recorder `(fn [m# & args#] (swap! ~atom-sym assoc m# (conj (-> ~atom-sym deref m#) (rest args#))))
        mimpls (map (partial proto-fn-with-proxy impl recorder) (fn-sigs proto))]
    `(let [~atom-sym (atom {})]
       (reify
         ~proto
         ~@mimpls
         Spy
         (calls [t#] (deref ~atom-sym))
         ))))

(defn- wrap-fn [impls m]
  (if-let [o (impls m)]
    `(fn [& args#] (if (fn? ~o) (apply ~o args#) ~o))
    `(fn [& args#] nil)))

(defmacro stub
  "Given a protocol and a hashmap of function implementations, returns a new implementation of that protocol with those
  implementations. If no function implementation is given for a method, that method will return `nil` when called."
  [proto impls]
  (let [sigs (fn-sigs proto)
        fns (map (fn [[m _]] (wrap-fn impls m)) sigs)
        mimpls (map proto-fn-with-impl fns sigs)]
    `(reify
       ~proto
       ~@mimpls)
    ))

(defmacro mock
  "Given a protocol and a hashmap of function implementations, returns a new implementation of that protocol with those
  implementations. The returned implementation is also a spy, allowing you to inspect and assert against its calls.
  See `spy` and `stub`."
  [proto impls]
  `(spy ~proto (stub ~proto ~impls)))
