(ns baleen.database
  (:require [crossref.util.config :refer [config]])
  (:require [korma.db :as kdb])
  (:require [korma.core :as k])
  (:require [korma.db :refer [mysql with-db defdb]])
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]])
  (:require [clj-time.coerce :as coerce]
            [clj-time.core :as clj-time]))


(kdb/defdb db (kdb/mysql
  {:db (:db-name config)
   :user (:db-username config)
   :password (:db-password config)
   :host (:db-host config)
   :naming {:keys ->kebab-case
            :fields ->snake_case}}))


(k/defentity citation-event
  (k/fields :id
            ; A unique string that describes the event, understandable by the particular source. Indexed by prefix, so unique things first.
            ; More than one redundant agent may try to insert (only one should succeed), so this is the unique key.
            ; e.g.
            ; Wikipedia: JSON of [old-id, new-id, title, wiki-server, doi]
            ; Twitter: JSON of [tweet-id, doi]
            :event-key
            ; The ID of the input event. Corresponds to an entry in input-event, which may or may not exist depending on the source.
            ; Doesn't form part of the identity, as two instances may compete to insert the same citation event, but only one will win (with its own event id).
            :input-event-id
            :doi
            :date
            ; ID of the citing entity in correct escaped URL format.
            :url
            ; The type of action. 
            ; e.g.
            ; Wikipedia: add, remove
            ; Twitter: tweet, retweet
            :action
            ; Has this been pushed upstream?
            :pushed
            ; Has this been flagged?
            :flagged)

   (k/prepare (fn [item]
                (if (:date item)
                  (assoc item :date (coerce/to-sql-time (:date item)))
                  item)))

   (k/transform (fn [item]
                  (if (:date item)
                    (assoc item :date (coerce/from-sql-time (:date item)))
                    item))))

(k/defentity input-event
  (k/fields :id :event-id :content :date :has-citation)
  (k/prepare (fn [item]
              (if (:date item)
                (assoc item :date (coerce/to-sql-time (:date item)))
                item)))

 (k/transform (fn [item]
                (if (:date item)
                  (assoc item :date (coerce/from-sql-time (:date item)))
                  item))))

(defn heartbeat []
  ; This will either work or fail.
  (k/select citation-event (k/limit 0)))