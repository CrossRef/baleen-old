# Baleen

Watch a live stream of events for citations of  DOIs. Currently supports Wikimedia RCStream.

## Wikipedia

Watches the RCStream (Recent Changes Stream) for edits and extracts the DOIs 

### TODO
 - reconnect on disconnection
 - retry requests

## Installation

Create `config.edn`. Create mysql database with `etc/schema/sql`.

The Wikipedia Recent Changes stream uses an obsolete version of Socket.IO, so we need to use an unsupported version. To install:

 - download and build per instructions at https://github.com/Gottox/socket.io-java-client
 - put the JAR in `lib`
 - `lein localrepo install lib/socketio.jar gottox/socketio "0.1"`
 - `lein run`

## License

Copyright © 2015 Crossref

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
