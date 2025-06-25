[![Clojars Project](https://img.shields.io/clojars/v/io.github.fourteatoo/clj-evohome.svg?include_prereleases)](https://clojars.org/io.github.fourteatoo/clj-evohome)
[![cljdoc badge](https://cljdoc.org/badge/io.github.fourteatoo/clj-evohome)](https://cljdoc.org/d/io.github.fourteatoo/clj-evohome)
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/fourteatoo/clj-evohome/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/fourteatoo/clj-evohome/tree/main)



# clj-evohome

A Clojure library to query and configure Honeywell EVO Home (aka TCC)
installations.

The library interact with the REST API of the cloud service.  Those
servers do the actual talking with your Honeywell devices.  That is,
you need an internet connection.


## Usage

Require the library in your source code

```clojure
(require '[clj-evohome.api :as eh])
```

First you need to authenticate yourself with the server

```clojure
(def c (eh/authenticate-client "username" "password"))
```

Get information about your account

```clojure
(def acc-info (eh/user-account-info c))
```

List all the installations belonging to a user

```clojure
(def insts (eh/installations-by-user c (:user-id acc-info)))
```

Get the installation at a specific location

```clojure
(def inst1 (installation-by-location c (get-in (first insts) [:location-info :location-id])))
```

Get a system status and change its mode

```clojure
(def system (-> inst1
                :gateways
                first
                :temperature-control-systems
                first))
(get-system-status c (:system-id system))
(set-system-mode c (:system-id system) :dayoff)
```

Get a specific zone's schedule

```clojure
(def zone (first (:zones system)))
(def sched (get-zone-schedule c (:zone-id zone)))
```

Set a zone schedule

```clojure
(set-zone-schedule c (:zone-id zone) sched)
```

Override a zone temperature

```clojure
(set-zone-temperature c (:zone-id zone) 17.5)
```

Cancel a zone override

```clojure
(cancel-zone-override c (:zone-id zone))
```

Get a location status

```clojure
(get-location-status c (get-in inst1 [:location-info :location-id]))
```

## Documentation

You can have a look at [![cljdoc](https://cljdoc.org/badge/io.github.fourteatoo/clj-evohome)](https://cljdoc.org/d/io.github.fourteatoo/clj-evohome)

or you can create your own local documentation with:

```shell
$ lein codox
```

and then read it with your favorite browser

```shell
$ firefox target/doc/index.html
```




## License

Copyright © 2022 Walter C. Pelissero

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
