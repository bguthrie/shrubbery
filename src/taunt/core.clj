(ns taunt.core
  (require [clojure.repl :refer [pst]]))

(defprotocol Spy
  (calls [t]))

(defn call-count [spy method & args]
  (-> spy calls method count))

(defn received?
  ([spy method] (received? spy method 1))
  ([spy method count] (= (call-count spy method) count)))

(defn fn-names [proto]
  (-> proto resolve var-get :sigs))

(defn proxied-fn [impl f [m sig]]
  "Given a protocol implementation, a function with side effects, and a protcol function signature, return
  a syntax-quoted protocol method implementation that calls the function, then proxies to the given implementation."
  (let [f-sym (-> m name symbol)
        args (-> sig :arglists first)]
    `(~f-sym                         ; Declare an implementation of the protocol function.
       ~args                         ; Render a vector with the proto-defined args.
       (~f ~m ~@args)                ; Call the given fn with the method name and splice any remaining args.
       (~f-sym ~impl ~@(rest args))) ; Call the underlying impl and pass whatever args remain.
    ))

(defmacro proxy-protocol [proto impl f]
  "Given a protocol, an implementation of that protocol, and some function f, presumably with side effects, returns
  a new implementation of that protocol that calls f with its method name and any remaining args. Each method returns
  the value of that method call as applied to the given impl."
  (let [mimpls (map (partial proxied-fn impl f) (fn-names proto))]
    `(reify
       ~proto
       ~@mimpls
       )))

(defmacro spying-proxy
  "Given a protocol and an implementation of that protocol, returns a new implementation of that protocol that counts
  the number of times each method was received. The returned protocol also implements `Spy`, which exposes those
  counts. Each method is proxied to the given impl after capture."
  [proto impl]
  (let [atom-sym (gensym "counts")
        recorder `(fn [m# & args#] (swap! ~atom-sym assoc m# (conj (-> ~atom-sym deref m#) args#)))
        mimpls (map (partial proxied-fn impl recorder) (fn-names proto))]
    `(let [~atom-sym (atom {})]
       (reify
         ~proto
         ~@mimpls
         Spy
         (calls [t#] (deref ~atom-sym))
         ))))
