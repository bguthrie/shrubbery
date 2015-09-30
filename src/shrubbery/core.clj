(ns shrubbery.core
  (:require [clojure.string :as str]))

(defprotocol Spy
  "A protocol for objects that expose call counts. `calls` should return a map of function names to lists of received
  args, one for each time the function is called."
  (calls [t])
  (proxied [t]))

(defprotocol Stub
  "A protocol for defining stubs. `protocol` should return a reference to the protocol that this stub implements.")

(defn protocol?
  "True if p is a protocol."
  [p]
  (boolean (:on-interface p)))

(defn stub?
  "True if s reifies Stub."
  [s]
  (instance? (:on-interface Stub) s))

(defn spy?
  "True if s reifies Spy."
  [s]
  (instance? (:on-interface Spy) s))

(defprotocol Matcher
  "A protocol for defining argument equality matching. Default implementations are provided for `Object` (equality),
  `Pattern` (regexp matching), and `IFn` (execution)."
  (matches? [matcher value]))

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
    (= matcher value))
  )

(def anything
  "A Matcher that always returns true."
  (reify Matcher
    (matches? [_ _] true)))

(defn call-count
  "Given a spy, a keyword method name, and an optional vector of args, return the number of times the spy received
  the method. If given args, filters the list of calls by matching the given args. Matched args may implement the
  `Matcher` protocol; the default implementation for `Object` is `=`."
  ([spy method]
   (-> (calls spy) (get method) (count)))
  ([spy method args]
  (->>
     (get (calls spy) method)
     (filter #(matches? args %))
     (count))))

(defn received?
  "Given a spy and a method reference, return true if the method has been called on the spy at least once. If given
  args, filters the list of calls by matching the given args. Matched args may implement the `Matcher` protocol;
  the default implementation for `Object` is `=`."
  ([spy method]
   (>= (call-count spy (-> method str keyword)) 1))
  ([spy method args]
   (>= (call-count spy (-> method str keyword) args) 1)))

(defn- find-proto-var
  "Attempts to find the protocol var representing the given class, or nil if none found."
  [^Class maybe-proto]
  (let [pkg-name   (->> (.getName maybe-proto) (re-find #"^(.*?)\.[^\.]+$") (second))
        ns-name    (str/replace pkg-name "_" "-")
        proto-name (.getSimpleName maybe-proto)]
    (when (find-ns (symbol ns-name))
      (find-var (symbol ns-name proto-name)))))

(defn protocols [o]
  "Given an object, attempt to return the set of all protocols it reifies. Warning: this may choke
  on nonstandard package and namespace choices. Pull requests and bug reports welcome."
  (->> (supers (class o))
       (map find-proto-var)
       (remove nil?)
       (map var-get)
       (set)))

(defn- reify-syntax-for-single-proto [proto f]
  (let [mimpls (map f (-> proto :sigs))]
    `(~(:on proto)
       ~@mimpls)))

(defn- proto-fn-with-proxy
  "Given a protocol implementation, a function with side effects, and a protcol function signature, return
  a syntax-quoted protocol method implementation that calls the function, then proxies to the given implementation."
  [proxy-sym proto f [m sig]]
  (let [args     (-> sig :arglists first)
        f-sym    (-> sig :name)
        proto-ns (-> proto :var meta :ns)
        f-ref    (symbol (str proto-ns) (str f-sym))]
    `(~f-ref ~args                        ; (some.ns/foo [this a b]
       (~f ~m ~@args)                     ;   ((fn [method this a b] ...) :foo this a b)
       (~f-ref ~proxy-sym ~@(rest args))) ;   (some.ns/foo proto-impl a b))
    ))

(defn- proto-spy-reify-syntax [atom-sym proxy-sym proto]
  (let [recorder `(fn [m# & args#] (swap! ~atom-sym assoc m# (conj (-> ~atom-sym deref m#) (rest args#))))]
    (reify-syntax-for-single-proto proto (partial proto-fn-with-proxy proxy-sym proto recorder))))

(declare ^:dynamic *proxy*)

(defn spy
  "Given an object implementing one or more protocols, returns a new object that records each call to underlying
  protocol functions, then proxies to the original implementation. The returned object also implements `Spy`, which
  exposes those calls. If an explicit list of protocols is not given, the list will be inferred from the given object."
  ([o]
   (spy o (protocols o)))
  ([o protos]
   (when (empty? protos)
     (throw (IllegalArgumentException. "No spyable protocols available for given object.")))

   (let [atom-sym (gensym "counts")
         proxy-sym (gensym "proxy")
         spy-syntax `(Spy (calls [t#] (deref ~atom-sym)) (proxied [t#] ~proxy-sym))
         all-protos-syntax (map (partial proto-spy-reify-syntax atom-sym proxy-sym) protos)
         everything `(let [~atom-sym (atom {})
                           ~proxy-sym *proxy*]
                       (reify
                         ~@(reduce concat (conj all-protos-syntax spy-syntax))))]

     (binding [*proxy* o]
       (eval everything)))))

(defprotocol Stubbable
  (reify-syntax-for-stub [thing]))

(extend-protocol Stubbable
  ; I'm leaving this here for historical reasons, but am making an explicit choice
  ; not to support this use-case. If you want to pass a function, just reify the
  ; protocol the old-fashioned way. I won't intern vars on the caller's behalf.
  ;
  ;clojure.lang.AFunction
  ;(reify-syntax-for-stub [thing arglist]
  ;  (let [fn-sym (gensym)]
  ;    (intern *ns* fn-sym thing)
  ;    `(~fn-sym ~@arglist)))
  clojure.lang.AFunction
  (reify-syntax-for-stub [_] (throw (RuntimeException. "Fns not supported in stub implementations")))
  clojure.lang.Symbol
  (reify-syntax-for-stub [thing] `'~thing)
  java.lang.Object
  (reify-syntax-for-stub [thing] thing)
  )

;; AProtocol{foo,bar,baz}, {:foo 5, :bar nil, :baz nil} -> '((foo [_] 5) (bar [_ _] nil) (baz [_ _ _] nil))
(defn- stub-fn [proto impl-hash [m sig]]
  (let [args              (-> sig :arglists first)
        f-sym             (-> sig :name)
        proto-ns          (-> proto :var meta :ns)
        f-ref             (symbol (str proto-ns) (str f-sym))
        stub-impl-or-nil  (get impl-hash m)
        stub-value        (when-not (nil? stub-impl-or-nil) (reify-syntax-for-stub stub-impl-or-nil))]
    `(~f-ref ~args ~stub-value)))

(defn- stub-reify-syntax [[proto impls]]
  (reify-syntax-for-single-proto proto (partial stub-fn proto impls)))

(defn- impl? [maybe-impl]
  (and (not (protocol? maybe-impl)) (map? maybe-impl)))

;; [AProto Impl? ...] -> [AProto Impl ...]
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

(defn stub
  "Given a variable number of protocols, each followed by an optional hashmap of simple implementations, returns a new
  object implementing those protocols. Where no function implementation is provided, calls to that protocol function
  will return `nil` rather than raising `IllegalArgumentException`. Functions as values are not supported, as they
  require side effects to define; for more complex stubs, prefer using `reify`."
  [& protos-and-impls]
  (when (or (empty? protos-and-impls) (not (protocol? (first protos-and-impls))))
    (throw (IllegalArgumentException. "Must provide at least one protocol to stub.")))

  (let [protos-and-impls (expand-proto-stubs protos-and-impls)
        stub-impls (map stub-reify-syntax protos-and-impls)
        everything (conj stub-impls `(Stub))]
    (eval
      `(reify
         ~@(reduce concat everything)))))

(defn mock
  "Given a protocol and a hashmap of function implementations, returns a new implementation of that protocol with those
  implementations. The returned implementation is also a spy, allowing you to inspect and assert against its calls.
  See `spy` and `stub`."
  [& protos-and-impls]
  (spy
    (apply stub protos-and-impls)))
