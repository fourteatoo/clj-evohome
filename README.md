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


There are two main components of the library; the low-level namespace
`fourteatoo.clj-evohome.api` which just wraps the REST API.  And the
higher-level namespace `fourteatoo.clj-evohome.core` which provides
a friendlier interface to the API.


## Usage

Require the library in your source code:

```clojure
(require '[fourteatoo.clj-evohome.api :as api])
```

Additionally you may want to require the higher-level interface

```clojure
(require '[fourteatoo.clj-evohome.core :as capi])
```


### Basic functionality (the `api` namespace)

In your code, first you need to authenticate yourself with the server

```clojure
(def c (api/authenticate-client "username" "password"))
```

The function `api/authenticate-client` returns an api/ApiClient record
that you need to pass to the other functions below.

Get information about your account with

```clojure
(def acc-info (api/get-user-account c))
```

List all the locations belonging to a user

```clojure
(def inst (api/get-installation c (:user-id acc-info)))
```

Get the installation at a specific location

```clojure
(def loc (api/get-location c (get-in (first inst) [:location-info :location-id])))
```

Get a system status and change its mode

```clojure
(def system (-> loc
                :gateways
                first
                :temperature-control-systems
                first))
(api/get-system-status c (:system-id system))
(api/set-system-mode c (:system-id system) :day-off)
```

Get a specific zone's schedule

```clojure
(def zone (first (:zones system)))
(def sched (api/get-zone-schedule c (:zone-id zone)))
```

Set a zone schedule

```clojure
;; [...]
(api/set-zone-schedule c (:zone-id zone) sched)
```

Override a zone temperature

```clojure
(api/set-zone-temperature c (:zone-id zone) 17.5)
```

Cancel a zone override

```clojure
(api/cancel-zone-override c (:zone-id zone))
```

Get a location status

```clojure
(api/get-location-status c (get-in loc [:location-info :location-id]))
```

### Friendlier interface (the `core` namespace)

Although the `fourteatoo.clj-evohome.api` namespace provides the
essential functionality of the REST API, beside the authentication
mechanism, not much is provided in terms of abstraction.

In the namespace `fourteatoo.clj-evohome.core` there is a thin layer
atop the basic `fourteatoo.clj-evohome.api`.  It simplifies aspects
like addressing your objects by path, rather than ID.  The names
asssigned by the user become identifiers.  Locations can be identified
with strings like ["Home"] or ["Shop"] and zones can be addressed by their
path, like ["Home" "Bedroom"] or ["Shop" "Toilet"].  Internally a copy
of the installation is cached, to reduce the amount of queries.  That
means, there will be a delay between, say, the installation of a new
thermostat and its appearence in you application.  Unless that's
something you do frequently, it hardly matters.

First you need to authenticate

```clojure
(def c (capi/authenticate-client "username" "password"))
```

It will return a slightly different ApiClient which needs to be passed
to the following functions.

```clojure
(def inst (capi/get-installation c))
```

```clojure
(def loc (capi/get-location c ["Home"))
```

```clojure
(def sched (capi/get-zone-schedule c ["Home" "Kitchen"]))
```

```clojure
(capi/set-zone-temperature c ["Home" "Bedroom"] 17.5)
```

```clojure
(capi/get-location-status c ["Home"])
```

... and so on.

If you prefer to work with the original IDs, you can print a reference
table of the devices in your installation:

```clojure
(capi/print-installation-index (capi/get-installation c))
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

The topology of the EVO Home system sees the user as the owner of an
installation.  The installation is a collection of locations.  To a
location belong one or more gateways.  To a gateway belong one or more
temperature control systems.  Each temperature control system has a
number of zones; the actuators and sensors.  Physically, if you bought
an EvoTouch panel, the temperature control system is also the gateway.

A single apartment installation has typically just one location, one
gateway, one temperature control system (which is the gateway too) and
several zones (one for each room).

All these components have a globally unique ID.  That is, all the API
primitives require just the zone, location, or system ID, to uniquely
identify your device/area.  The locations and the zones have a name,
too, that can be assigned by the user.  The locations will have names
like "Home" or "Office", while zones will have names like "Kitchen",
"Bedroom", "Kids", "Living room", etc.

The low-level API requires you to use the IDs, while there is an
higher-level layer that allows you to use paths, to identify your
devices.  That is `["Home" "Kitchen"]` rather than the zone ID
`"162534"`.

Temperature control systems have user-defined modes.  Those are always
referenced by name; names such as `:day-off`, `:away`, `:auto`, etc.
The function `api/set-system-mode` works on a specific control system,
not on the entire location it belongs to.  The function
`capi/set-location-mode` applies the mode to all the systems belonging
to the same location, provided the mode is supported by all the
systems within.



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
