# Baleen

Watch Wikipedia / Wikimedia for citations of DOIs. Live-stream of DOIs being cited and uncited.

Note: this will download a copy of every article every time it's downloaded.

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
