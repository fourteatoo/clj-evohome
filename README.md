[![Clojars Project](https://img.shields.io/clojars/v/io.github.fourteatoo/clj-evohome.svg?include_prereleases)](https://clojars.org/io.github.fourteatoo/clj-evohome)
[![cljdoc badge](https://cljdoc.org/badge/io.github.fourteatoo/clj-evohome)](https://cljdoc.org/d/io.github.fourteatoo/clj-evohome)
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/fourteatoo/clj-evohome/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/fourteatoo/clj-evohome/tree/main)
[![Coverage Status](https://coveralls.io/repos/github/fourteatoo/clj-evohome/badge.svg)](https://coveralls.io/github/fourteatoo/clj-evohome)



# clj-evohome

A Clojure library to query and configure Honeywell EVO Home (aka TCC)
installations.

The library interacts with the REST API of the cloud service.  Those
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
(def acc-info (eh/get-user-account c))
```

List all the installations belonging to a user

```clojure
(def insts (eh/get-installations c (:user-id acc-info)))
```

Get the installation at a specific location

```clojure
(def inst1 (eh/get-installation-at-location c (get-in (first insts) [:location-info :location-id])))
```

Get a system status and change its mode

```clojure
(def system (-> inst1
                :gateways
                first
                :temperature-control-systems
                first))
(eh/get-system-status c (:system-id system))
(eh/set-system-mode c (:system-id system) :day-off)
```

Get a specific zone's schedule

```clojure
(def zone (first (:zones system)))
(def sched (eh/get-zone-schedule c (:zone-id zone)))
```

Set a zone schedule

```clojure
(eh/set-zone-schedule c (:zone-id zone) sched)
```

Override a zone temperature

```clojure
(eh/set-zone-temperature c (:zone-id zone) 17.5)
```

Cancel a zone override

```clojure
(eh/cancel-zone-override c (:zone-id zone))
```

Get a location status

```clojure
(eh/get-location-status c (get-in inst1 [:location-info :location-id]))
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

## Object tree

The topology of the EVO Home system sees the user as the owner of
different locations (installations).  To a location belong one or more
gateways.  To a gateway belong one or more temperature control
systems.  Each temperature control system has a number of zones; the
actuators and sensors.  Physically, if you bought an EvoTouch panel,
the temperature control system is also the gateway.

A single apartment installation may have just one location, one
gateway, one temperature control system (which is the gateway too) and
several zones (one for each room).

All these components have a globally unique ID.  That is, all the API
primitives require just the zone, location, or system ID, to uniquely
identify your device/area.  The locations and the zones have a name,
too, that can be assigned by the user.  The locations will have names
like "Home" or "Office", while zones will have names like "Kitchen",
"Bedroom", "Kids", "Livingroom", etc.

The low-level API requires you to use the IDs, while there is an
higher-level layer that allows you to use name paths, to identify your
devices.  That is `["Home" "Kitchen"]` rather than `"162534"`.


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
