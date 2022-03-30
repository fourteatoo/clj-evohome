(defproject fourtytoo/clj-evohome "0.1.0-SNAPSHOT"
  :description "A simple Clojure interface to Honeywell EVO Home"
  :url "http://github.com/fourtytoo/clj-evohome"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [clojure.java-time "0.3.3"]
                 [clj-http "3.12.3"]
                 [cheshire "5.10.1"]
                 [camel-snake-kebab "0.4.2"]]
  :repl-options {:init-ns clj-evohome.api2})
