# shrubbery

A stubbing, spying, and mocking library for Clojure protocols.

## Purpose

If you've ever used Midje's `given` macro, you understand the occasional need to test functions that have side effects. 
Luckily, there's a better way.

Clojure protocols, for example, are a _great_ way to encapsulate operations with side effects, but suffer from a general
lack of test tooling. Shrubbery provides a small set of basic building blocks for working with them:

 * `stub`, which accepts a variable list of protocols and a optional hashmap of simple value implementations and
   returns an object that reifies all given protocols;
 * `spy`, which accepts an object with at least one protocol implementation and returns a new implementation that 
    tracks the number of times each of its members were called;
 * `mock`, which wraps a `stub` in a `spy`, allowing callers to supply basic function implementations _and_ assert
    against those calls; and
 * `received?`, which in conjunction with the `Matcher` protocol provides a way to query spies programmatically for
   test assertion purposes.
 
Both spies and stubs can be used more or less independently; any protocol implementation may be wrapped by a spy, and 
stubs need not be spies to provide basic test utility.

Shrubbery is test framework-agnostic, has no external dependencies, makes no attempt to perform runtime var replacement
(unlike `given`) and uses no macros. It is simply meant to provide basic protocol implementation support in the meagre 
hope that it makes your tests (and your day) slightly more pleasant.

### Before You Begin

If you aren't already using a library like [Component](https://github.com/stuartsierra/component) to structure your 
Clojure application, or do not otherwise make much use of protocols, you may not find this library very interesting. 
Additionally, for more background on mocking as a strategy, you may find it helpful to read Martin Fowler's classic article on
the subject, [Mocks Aren't Stubs](http://martinfowler.com/articles/mocksArentStubs.html).

### Caveats

Shrubbery uses protocol reflection to reify them programmatically using `eval`. I don't especially like using `eval` or 
recommend others use it, but if there's a way to reify protocols dynamically without resorting to it then I haven't 
found it. Some somewhat-obvious use cases suffer for having to drop down into syntax parsing––for example,
in limiting the kinds of simple values you can pass as `stub` implementations.

## Releases

Shrubbery is published to [clojars.org](https://clojars.org/com.gearswithingears/shrubbery).

[![Clojars Project](http://clojars.org/com.gearswithingears/shrubbery/latest-version.svg)](http://clojars.org/com.gearswithingears/shrubbery)

## Usage

### Stubs

Stubs are essentially sugar over `reify`, allowing convenient dynamic protocol reification. By default,
a stub without implementations simply returns `nil` for all method calls rather than throwing an
`IllegalArgumentException`. Optionally, return values for some or all members of named protocols may be provided using a 
hashmap of method-name to value.

Once defined, stubs cannot currently be altered, though they may be introspected; see the `Stub` protocol.

```clojure
(ns example
  (:require [clojure.test :refer :all]
            [shrubbery.core :refer :all]))

(defprotocol DbQueryClient
  (select [t sql]))
  
(defprotocol DbUpdateClient
  (update [t sql values]))

(defn find-user [client id]
  (select client (str "select * from users where id = " id)))
  
(defn set-user-name [client user]
  (update client (str "update users set name = ? where id = ?") [(:name user) (:id user)]))

(deftest test-find-user
  (let [subject (stub DbUpdateClient DbQueryClient)]
    (is (nil? (find-user subject 42))))
    (is (nil? (set-user-name subject {:id 1 :name "Guybrush Threepwood"})))
  (let [subject (stub 
                  DbUpdateClient {:update 1} 
                  DbQueryClient {:select "wow"}
                  SomeOtherProtocol
                  AFourthProtocol)]
    (is (= "wow" (find-user subject 42))))
    (is (= 1 (set-user-name subject {:id 1 :name "Guybrush Threepwood"})))
    ))
```

### Spies

A test spy is an object that understands how many times its members have been called, and tracks the arguments those
members were called with. Shrubbery spies take an object that implements at least one protocol, attempts to infer the
protocols it implements, and returns a new implementation that proxies all calls to the starting implementation, tracking
their usages along the way. 

Note that Shrubbery provides no attempt to _automatically_ verify call counts, as with classic mocking frameworks: 
currently, you as the test author are expected to perform your own assertions against the spy once the relevant methods
have been called.

Spies are queried directly use `calls`, which simply returns a hashmap of all calls, or indirectly using `call-count` 
and `received?`, both of which allow you to filter the count. Each function comes in two forms: given a
spy and a var referring to a function, it returns the appropriate calls; if additionally given a vector of expected 
arguments, it will limit the query to calls matching those arguments.

Special support is provided for querying arguments against certain objects via the `Matcher` protocol; in particular, 
note that regular expressions are treated as if they are intended to be matched against strings rather than checked 
for direct object equality. This is useful in cases where you might want to make a rough assertion against a string's
contents but don't care about an exact match.

As with stubs, once defined, spies cannot currently be altered, though they may be introspected; see the `Spy` protocol.
 
```clojure
(ns example
  (:require [clojure.test :refer :all]
            [shrubbery.core :refer :all]))

(defprotocol DbClient
  (select [t sql]))
  
(def fake-db-client
  (reify DbClient
    (select [t sql] {:id 1})))
    
(defn find-user [client id]
  (select client ...))

(deftest test-find-user
  (let [subject (spy fake-db-client)]
    (is (not (received? subject select)))
    (is (= {:id 1} (find-user subject 42)))
    (is (received? subject select))
    (is (received? subject select [42]))
    ))
```

### Mocks

Mocks combine the call count capabilities of a spy with the sugar of stubs. They act like a stub for the purposes of
initial definition but act like a spy for functions like `call-count` and `received?`. More precisely, they first
call `stub` with the given arguments, then wrap that stub in a `spy`.

```clojure
(ns example
  (:require [clojure.test :refer :all]
            [shrubbery.core :refer :all]))

(defprotocol DbClient
  (select [t sql]))

(defn find-user [client id]
  (select client ...))

(deftest test-find-user
  (let [subject (mock DbClient {:select {:id 1}})]
    (is (not (received? subject select)))
    (is (= {:id 1} (find-user subject 42)))
    (is (received? subject select))
    (is (received? subject select [42]))
    ))
```

### Matchers

Sometimes it's helpful, when checking the spies and mocks for the arguments they're called with, to perform partial
matching rather than full equality matching. Shrubbery defines a protocol, `Matcher`, with one function, `matches?`,
implements some special match cases. Regular expressions are one notable case where this is useful. For example:

```clojure
(deftest test-db-client
  (let [mock-db (mock DbClient)]
    (select mock-db "select * from users")
    (is (received? mock-db select ["select * from users"]))
    (is (not (received? mock-db select ["select"])))
    (is (received? mock-db select [#"select"]))
    (is (not (received? mock-db select [#"select$"])))
    (is (received? mock-db select [anything]))
    ))
```

Shrubbery's `match?` function performs a standard `=` check by default, but defines several special cases:

 * `java.util.regex.Pattern` objects use `re-seq` on the received object
 * `clojure.lang.ArraySeq` objects performs `match?` on all members of the received object
 * `clojure.lang.Fn` objects are invoked directly with the received object
 * `anything` is a special matcher that always returns true
 
## License

![Then when you have found the shrubbery](https://31.media.tumblr.com/e72f365e1656130bbaebd2a2431c958b/tumblr_nia9ciTmpj1u0k6deo4_250.gif)

Copyright © 2015 Brian Guthrie

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
