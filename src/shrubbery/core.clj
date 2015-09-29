(ns shrubbery.core
  (:import (clojure.lang Var)))

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

(defn received?
  ([spy method]
   (>= (call-count spy (-> method str keyword)) 1))
  ([spy method args]
   (>= (call-count spy (-> method str keyword) args) 1)))

(defn- namespace-from-parts [package-parts]
  (->> package-parts
       (map #(clojure.string/replace % #"_" "-"))
       (clojure.string/join ".")))

(defn- namespace-qualified-symbol [maybe-proto]
  (let [parts (-> (.getName maybe-proto) (clojure.string/split #"\."))
        ns-name (namespace-from-parts (butlast parts))
        maybe-ns (find-ns (symbol ns-name))
        proto-name (last parts)
        maybe-var (symbol ns-name proto-name)]
    (when maybe-ns
      (find-var maybe-var))))

(defn protocols [o]
  "Given an object, attempt to return the set of all protocols it reifies. Warning: this is buggy, and may choke
  on nonstandard package and namespace choices. Pull requests and bug reports welcome."
  (->> (supers (class o))
       (map namespace-qualified-symbol)
       (remove nil?)
       (map var-get)
       (set)
       ))

(declare ^:dynamic *proxy*)

(defn- proto-fn-with-proxy
  "Given a protocol implementation, a function with side effects, and a protcol function signature, return
  a syntax-quoted protocol method implementation that calls the function, then proxies to the given implementation."
  [proxy-sym proto f [m sig]]
  (let [args (-> sig :arglists first)
        f-sym (-> sig :name)
        proto-ns (-> proto :var meta :ns)
        f-ref (symbol (str proto-ns) (str f-sym))]
    `(~f-ref ~args                        ; (some.ns/foo [this a b]
       (~f ~m ~@args)                     ;   ((fn [method this a b] ...) :foo this a b)
       (~f-ref ~proxy-sym ~@(rest args))) ;   (some.ns/foo proto-impl a b))
    ))

(defn spy
  "Given a protocol and an implementation of that protocol, returns a new implementation of that protocol that counts
  the number of times each method was received. The returned implementation also implements `Spy`, which exposes those
  counts. Each method is proxied to the given impl after capture."
  [aproxy proto]
  (let [atom-sym (gensym "counts")
        proxy-sym (gensym "proxy")
        recorder `(fn [m# & args#] (swap! ~atom-sym assoc m# (conj (-> ~atom-sym deref m#) (rest args#))))
        mimpls (map (partial proto-fn-with-proxy proxy-sym proto recorder) (-> proto :sigs))]

    (binding [*proxy* aproxy]
      (eval
        `(let [~atom-sym (atom {})
               ~proxy-sym *proxy*]
          (reify
            ~(:on proto)
            ~@mimpls
            Spy
            (calls [t#] (deref ~atom-sym))
            (proxied [t#] ~proxy-sym)
            ))))))

(defprotocol Stubbable
  (reify-syntax-for-stub [thing arglist]))

(extend-protocol Stubbable
  ; I'm leaving this here for historical reasons, but am making an explicit choice
  ; not to support this use-case. If you want to pass a function, just reify the
  ; protocol the old-fashioned way. I won't intern vars on the caller's behalf.
  ;
  ;clojure.lang.IFn
  ;(reify-syntax-for-stub [thing arglist]
  ;  (let [fn-sym (gensym)]
  ;    (intern *ns* fn-sym thing)
  ;    `(~fn-sym ~@arglist)))
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
      (let [arglist           (-> proto-fn-sig :arglists first)
            gensymmed-arglist (vec (map (comp gensym symbol) arglist))
            stub-impl-or-nil  (get impl-hash proto-fn-name)
            stub-value        (when-not (nil? stub-impl-or-nil) (reify-syntax-for-stub stub-impl-or-nil gensymmed-arglist))]
        (list (-> proto-fn-name name symbol) gensymmed-arglist stub-value)))
    (-> proto :sigs)))

(defn stub-syntax->reify-syntax [proto impls]
  (cons (:on proto) (impl-stub-syntax->impl-reify-syntax proto impls)))

(defn stub
  "Given a protocol and a hashmap of function implementations, returns a new implementation of that protocol with those
  implementation. Functions as values are not supported, as they would require side effects to define; for more complex
  stubs, prefer `reify`."
  ([proto] (stub proto {}))
  ([proto impls]
    (eval
      `(reify
         ~@(stub-syntax->reify-syntax proto impls)))))

(defn mock
  "Given a protocol and a hashmap of function implementations, returns a new implementation of that protocol with those
  implementations. The returned implementation is also a spy, allowing you to inspect and assert against its calls.
  See `spy` and `stub`."
  ([proto]
   (mock proto {}))
  ([proto impls]
   (spy (stub proto impls) proto)))
