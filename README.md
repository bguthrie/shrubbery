# shrubbery

A stubbing, spying, and mocking library for Clojure protocols that uses no macros, defines no vars, and is compatible
with any test framework.

## Purpose

If you've ever unit-tested a stateful Clojure application, you may have found yourself needing to address side effects.
For example, a function may query a database or invoke an external web service. One common way to manage these side
effects is by declaring and implementing protocols that encapsulate the external call. This has the benefit of making
them explicit and easily testable. [Component](https://github.com/stuartsierra/component) is one framework that
encourages this approach.

Although Clojure protocols are a great way to encapsulate operations with side effects, they suffer from a general
lack of test tooling. Shrubbery provides a small set of basic building blocks for creating protocol implementations
specifically designed for testing.

Shrubbery is test framework-agnostic, has no external dependencies, makes no attempt to perform runtime var replacement
and uses no macros. It is simply meant to provide basic protocol implementation support for things like spies and stubs
in the meagre hope that it makes your tests (and your day) slightly more pleasant. To that end, it offers three
test constructs:

 * `stub`, which accepts a variable list of protocols and a optional hashmap of simple value implementations and
   returns an object that reifies all given protocols;
 * `spy`, which accepts an object with at least one protocol implementation and returns a new implementation that 
   tracks the number of times each of its members were called (queryable with `calls` and `received?`); and
 * `mock`, which wraps a `stub` in a `spy`, allowing callers to supply basic function implementations _and_ assert
   against those calls.
 
Spies and stubs can be used independently; any protocol implementation may be wrapped by a spy, and stubs need not
themselves be spies.

Spies and mocks may be queried, for test assertion purposes, with the following two functions:

 * `call-count`, which accepts a spy, a var, and an optional vector of arguments, and returns the call count for that
   spy's var, give those specific arguments if applicable.
 * `received?`, which returns true if the `call-count` for the given spy returns is greater than or equal to 1.

Additionally, Shrubbery exposes `clojure.test` sugar in the form of `shrubbery.clojure.test/received?`.

### Before You Begin

If you aren't already using a library like [Component](https://github.com/stuartsierra/component) to structure your 
Clojure application, or do not otherwise make much use of protocols, you may not find this library very interesting. 
Additionally, for more background on mocking as a strategy, you may find it helpful to read Martin Fowler's classic
article on the subject, [Mocks Aren't Stubs](http://martinfowler.com/articles/mocksArentStubs.html).

### Caveats

Shrubbery uses protocol reflection and `eval` to perform dynamic reification without resorting to macros. Some
somewhat-obvious use cases suffer for having to drop down into syntax parsing––for example, in limiting the kinds of
simple values you can pass as stub implementations. If you know of a better way to reify protocol programmatically,
please get in touch.

## Releases

Shrubbery is published to [clojars.org](https://clojars.org/com.gearswithingears/shrubbery).

[![Clojars Project](http://clojars.org/com.gearswithingears/shrubbery/latest-version.svg)](http://clojars.org/com.gearswithingears/shrubbery)

## Usage

[Complete API docs](https://bguthrie.github.io/shrubbery/) are available. For a gentler introduction, see below.

### Stubs

Stubs are essentially sugar over `reify`, allowing convenient dynamic protocol reification. By default,
a stub without implementations simply returns `nil` for all method calls rather than throwing an
`IllegalArgumentException`. Optionally, return values for some or all members of named protocols may be provided using a 
map of method-name to value.

Stubs may be introspected via the `Stub` protocol, and immutable transformations can be made with the `returning`
function.

```clojure
(defprotocol DbQueryClient
  (select [client sql] "Returns a list of database records matching the given query.")
  (insert [client sql] "Inserts a record using the given SQL."))

(def database-stub
  (stub DbQueryClient
    {:select [{:name "Guybrush"}]
     :insert (throws RuntimeException "Insert not supported"})

(defn find-user [client id]
  (select client (str "select * from users where id = " id)))

(deftest test-find-user
  (is (nil? (find-user (stub DbQueryClient) 42)))
  (is (= "Guybrush" (-> database-stub (find-user 42) (first) (:name)))))
```

### Spies

A test spy is an object that understands how many times its members have been called, and tracks the arguments those
members were called with. Shrubbery's `spy` takes a Clojure object that implements at least one protocol, attempts to
infer the protocol(s) it implements, and returns a new implementation that proxies all calls to the starting
implementation, tracking their usages along the way.

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

Once defined, spies cannot currently be altered, though they may be introspected; see the `Spy` protocol.
 
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
    (is (received? subject select [42]))))
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
    (is (received? subject select [42]))))
```

### Matchers

Sometimes it's helpful, when checking the spies and mocks for the arguments they're called with, to perform partial
matching rather than full equality matching. Shrubbery defines a protocol, `Matcher`, with one function, `matches?`,
which performs a standard `=` check by default but defines several special cases:
Regular expressions are one notable case where this is useful. For example:

 * `java.util.regex.Pattern` objects use `re-seq` on the received object.
 * `clojure.lang.ArraySeq` objects performs `match?` on all members of the received object.
 * `clojure.lang.Fn` objects are invoked directly with the received object.
 * `anything` is a special matcher that always returns true.

As an example of this in use with regexes specifically:

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

## License

![Then when you have found the shrubbery](https://31.media.tumblr.com/e72f365e1656130bbaebd2a2431c958b/tumblr_nia9ciTmpj1u0k6deo4_250.gif)

Copyright © 2015 Brian Guthrie

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
