# shrubbery

A stubbing, spying, and mocking library for Clojure protocols.

## Purpose

Clojure protocols are a great way to encapsulate operations with side effects. However, even a great abstraction
can benefit from a little test support. Shrubbery provides three basic building blocks for working with them:
`spy`, which wraps a protocol implementation and adds call counting; `stub`, which provides test-friendly sugar
over `reify`; and `mock`, which combines a stub with a spy. Each component, however, can be used independently;
any protocol implementation may become a spy, and stubs need not be spies to be useful.

Unlike other libraries, Shrubbery is test framework-agnostic and makes no attempt to perform runtime var replacement
or add unusual syntax. It is simply meant to provide basic protocol implementation support in the meagre hope that
it makes your day slightly more pleasant. 

### Before You Begin

If you aren't already using a library like [Component](https://github.com/stuartsierra/component) to structure your 
Clojure application, or do not otherwise enjoy using protocols, you may not find this library very interesting. 
Likewise, you may find it helpful to read Martin Fowler's classic article on mock objects, 
[Mocks Aren't Stubs](http://martinfowler.com/articles/mocksArentStubs.html).

## Usage

### Spies

A test spy is an object that understands how many times it's been called, and with what. Shrubbery spies take a protocol
and some pre-existing implementation of it and tracks those calls, delegating to the underlying implementation where
necessary. Note that Shrubbery provides no attempt to automatically verify call counts as with classic mocking: in
its current implementation, the test writer is expected to supply their own call assertions.

Spies are introspected directly using `call-count` or more commonly using `received?`. Each function comes in two forms:
if no argument vector is given, it simply asks for _any_ calls to the named method; if it is, calls will be limited
to those whose signature matches the given arguments.

Special support is provided for certain objects; in particular, note that regular expressions are treated as if they
are intended to be matched against strings rather than checked for object equality.
 
```clojure
(defprotocol DbClient
  (select [t sql]))
  
(def fake-db-client
  (reify DbClient
    (select [t sql] {:id 1})))
    
(defn find-user [client id]
  (select client ...))

(deftest test-find-user
  (let [subject (spy DbClient fake-db-client)]
    (is (not (received? subject :select)))
    (is (= {:id 1} (find-user subject 42)))
    (is (received? subject :select))
    (is (received? subject :select [42]))
    ))
```

### Stubs

Stubs are essentially sugar over `reify`. If no implementations are given then a new protocol implementation is returned
whose members always return `nil`; if it is, then its arguments are treated slightly differently depending on their
type. If functions are provided they are used unaltered as the protocol implementation; if immediate values are
provided then they are wrapped in functions and _those_ become the implementations.

```clojure
(defprotocol DbClient
  (select [t sql]))
      
(defn find-user [client id]
  (select client (str "select * from users where id = " id)))

(deftest test-find-user
  (let [subject (stub DbClient {})]
    (is (nil? (find-user subject 42))))
  (let [subject (stub DbClient {:select "wow"})]
    (is (= "wow" (find-user subject 42))))
  (let [subject (stub DbClient {:select (fn [_ sql] (str "sql was " sql)})]
    (is (= "sql was select * from users where id = 42"
           (find-user subject 42))))
  )
```

### Mocks

Mocks combine the call count capabilities of a spy with the sugar of stubs.

```clojure
(defprotocol DbClient
  (select [t sql]))

(defn find-user [client id]
  (select client ...))

(deftest test-find-user
  (let [subject (mock DbClient {:select {:id 1}})]
    (is (not (received? subject :select)))
    (is (= {:id 1} (find-user subject 42)))
    (is (received? subject :select))
    (is (received? subject :select [42]))
    ))
```

## License

![Then when you have found the shrubbery](https://31.media.tumblr.com/e72f365e1656130bbaebd2a2431c958b/tumblr_nia9ciTmpj1u0k6deo4_250.gif)

Copyright © 2015 Brian Guthrie

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
