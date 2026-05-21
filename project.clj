(defproject io.github.fourteatoo/clj-evohome "1.3.1-SNAPSHOT"
  :description "A simple interface to Honeywell EVO Home"
  :url "http://github.com/fourteatoo/clj-evohome"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.1"]
                 [clojure.java-time "1.4.3"]
                 [hato "1.0.0"]
                 [cheshire "6.0.0"]
                 [camel-snake-kebab "0.4.3"]]
  :profiles {:dev {:plugins [[lein-codox "0.10.8"]
                             [lein-cloverage "1.2.4"]]}}
  :repl-options {:init-ns fourteatoo.clj-evohome.api}
  :lein-release {:deploy-via :clojars})
