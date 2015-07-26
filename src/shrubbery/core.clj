(ns shrubbery.core)

(defprotocol Spy
  "A protocol for objects that expose call counts. `calls` should return a map of function names to lists of received
  args, one for each time the function is called."
  (calls [t])
  (proxied [t]))

(defprotocol Matcher
  "A protocol for defining argument equality matching. Default implementations are provided for `Object` (equality),
  `Pattern` (regexp matching), and `IFn` (execution)."
  (matches? [matcher value]))

(defprotocol Stub
  "A protocol for defining stubs. `protocol` should return a reference to the protocol that this stub implements."
  (protocol [t]))

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
  clojure.lang.IPersistentVector
  (matches? [matcher value]
    (->> (map matches? matcher value)
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
   (-> (calls spy) (get method) (count)))
  ([spy method args]
  (->>
     (get (calls spy) method)
     (filter #(matches? args %))
     (count))))

(defmacro received?
  ([spy method]
   `(>= (call-count ~spy ~(-> method str keyword)) 1))
  ([spy method args]
   `(>= (call-count ~spy ~(-> method str keyword) ~args) 1)))

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

(defmacro spy
  "Given a protocol and an implementation of that protocol, returns a new implementation of that protocol that counts
  the number of times each method was received. The returned implementation also implements `Spy`, which exposes those
  counts. Each method is proxied to the given impl after capture."
  [proto proxy]
  (let [atom-sym (gensym "counts")
        recorder `(fn [m# & args#] (swap! ~atom-sym assoc m# (conj (-> ~atom-sym deref m#) (rest args#))))
        mimpls (map (partial proto-fn-with-proxy proxy recorder) (fn-sigs proto))]
    `(let [~atom-sym (atom {})]
       (reify
         ~proto
         ~@mimpls
         Spy
         (calls [t#] (deref ~atom-sym))
         (proxied [t#] ~proxy)
         ))))

(defprotocol Stubbable
  (reify-syntax-for-stub [thing arglist]))

(extend-protocol Stubbable
  clojure.lang.IFn
  (reify-syntax-for-stub [thing arglist]
    (let [fn-sym (gensym)]
      (intern *ns* fn-sym thing)
      `(~fn-sym ~@arglist)))
  clojure.lang.Symbol
  (reify-syntax-for-stub [thing arglist]
    `'~thing)
  java.lang.Object
  (reify-syntax-for-stub [thing arglist] thing)
  )

;; AProtocol{foo,bar,baz}, {:foo 5, :bar nil, :baz nil} -> '((foo [_] 5) (bar [_ _] nil) (baz [_ _ _] nil))
(defn- impl-stub-syntax->impl-reify-syntax [proto impl-hash]
  (map
    (fn [[proto-fn-name proto-fn-sig]]
      (let [arglist (-> proto-fn-sig :arglists first)
            gensymmed-arglist (vec (map (comp gensym symbol) arglist))
            stub-impl-or-nil (get impl-hash proto-fn-name)
            stub-value (when-not (nil? stub-impl-or-nil) (reify-syntax-for-stub stub-impl-or-nil gensymmed-arglist))]
        (list (-> proto-fn-name name symbol) gensymmed-arglist stub-value)))
    (-> proto :sigs)))

(defn stub-syntax->reify-syntax [proto impls]
  (cons (:on proto) (impl-stub-syntax->impl-reify-syntax proto impls)))

(defn stub
  ([proto] (stub proto {}))
  ([proto impls]
    (eval
      `(reify
         ~@(stub-syntax->reify-syntax proto impls)))))

(defmacro mock
  "Given a protocol and a hashmap of function implementations, returns a new implementation of that protocol with those
  implementations. The returned implementation is also a spy, allowing you to inspect and assert against its calls.
  See `spy` and `stub`."
  ([proto]
   `(mock ~proto {}))
  ([proto impls]
   `(spy ~proto (stub ~proto ~impls))))
