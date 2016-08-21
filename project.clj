(defproject com.gearswithingears/shrubbery "0.4.0"
  :description "A stubbing, spying, and mocking library for Clojure protocols."
  :url "http://github.com/bguthrie/shrubbery"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]]
                   :plugins [[codox "0.8.13"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :test {:aot [shrubbery.core-test]}}
  :codox {:defaults {:doc/format :markdown}
          :exclude [shrubbery.core-test]})

