# clj-evohome

A Clojure library to query and configure a Honeywell EVO Home system
(aka TCC).

## Usage

Require the library in your source code. API2 is the way to go:

```clojure
(require '[clj-evohome.api2 :as eh])
```

First and foremost you need to connect to the cloud

```clojure
(def c (connect "username" "password"))
```

To get information about your account in the cloud

```clojure
(user-account-info c)
```

The following should not require explanation

```clojure
(installations-by-user c your-user-id)
(full-installation c your-installation-id)
(get-system-status c your-system-id)
(set-system-mode c your-system-id :dayoff)
(def sched (get-zone-schedule c your-zone-id))
(set-zone-schedule c your-zone-id your-new-schedule)
(set-zone-temperature c your-zone-id 17.5)
(cancel-zone-override c your-sone-id)
(get-location-status c your-location-id)
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
