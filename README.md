# Baleen

Collect live citations of DOIs, display, store and push to Crossref DET. Consumes live stream from Wikipedia. Modular, for future sources.

## Source types

### Wikipedia

Live stream of edits: http://wikipedia.labs.crossref.org

Watches all Wikimedia sites (or sites of your choosing) for edits and extracts the DOIs. Watches the Recent Changes stream, fetches old and new versions, compares for DOI citations and uncitations. Note that this source engages in a large amount of network activity!

## Installation

Create mysql database with `etc/schema/sql`. 

The Wikipedia Recent Changes stream uses an obsolete version of Socket.IO, so we need to use an unsupported version. To install:

 - download and build per instructions at https://github.com/Gottox/socket.io-java-client
 - put the JAR in `lib`
 - `lein localrepo install lib/socketio.jar gottox/socketio "0.1"`
 - `lein run`

## To run

Create `config.edn`, based on `config.edn.example`.

 - Include mysql connection information, select a source. 
 - Wikimedia
   - For development, for the `:subscribe` parameter choose a popular Wikimedia site such as "en.wikipedia.org"
   - For production, you can choose a wiki or `"*"` for all. Beware this results in heavy traffic!

The sources are pluggable, but one process runs as an agent for only one source at once. Choose a source in the config file and run. In production, run on more than one machine for resilience: multiple streams are de-duped.

    lein run

Each source will re-connect if it becomes disconnected. If the program crashes it should be restarted automatically, e.g. with supervisord.

## License

Copyright © 2015 Crossref

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
