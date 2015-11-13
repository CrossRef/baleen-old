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


; (k/defentity event
;   (k/fields :id
;             :old-id
;             :new-id
;             :doi
;             :date
;             :server
;             :title
;             :url
;             :action)
;   (k/prepare (fn [item] (assoc item :date (coerce/to-sql-time (:date item)))))
;   (k/transform (fn [item] (assoc item :date (coerce/from-sql-time (:date item))))))

; (defn insert [action old-id new-id doi server title url date]
;   (k/insert event (k/values {:action action :old-id old-id :new-id new-id :doi doi :date date :server server :title title :url url})))

; (defn get-events-page [from-id]
;   (if from-id
;     (k/select event (k/where (< :id from-id)) (k/order :id :DESC) (k/limit page-size))
;     (k/select event (k/order :id :DESC) (k/limit page-size))))

; (defn get-citations-for-period [[end start]]
;   (->
;     (k/select event (k/aggregate (count :*) :cnt)
;       (k/where (>= :date (coerce/to-sql-time start)))
;       (k/where (< :date (coerce/to-sql-time end))))
;     first
;     :cnt))

(k/defentity citation-event
  (k/fields :id
            ; A unique string that describes the event, understandable by the particular source. Indexed by prefix, so unique things first.
            ; More than one redundant agent may try to insert (only one should succeed), so this is the unique key.
            ; e.g.
            ; Wikipedia: JSON of [old-id, new-id, title, wiki-server, doi]
            ; Twitter: JSON of [tweet-id, doi]
            :event-key
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
            :pushed)
   (k/prepare (fn [item] (assoc item :date (coerce/to-sql-time (:date item)))))
   (k/transform (fn [item] (assoc item :date (coerce/from-sql-time (:date item))))))
