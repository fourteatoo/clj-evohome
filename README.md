# clj-evohome

A Clojure library to query and configure a Honeywell EVO Home system
(aka TCC).

## Usage

Require the library in your source code. The API2 is probably what you
want to use:

```clojure
(require '[clj-evohome.api2 :as eh])
```

First and foremost you need to connect to the cloud

```clojure
(def c (eh/connect "username" "password"))
```

To get information about your account

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
