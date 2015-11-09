(ns wikipediawatcher.database
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


(k/defentity event
  (k/fields :old-id
            :new-id
            :doi
            :inserted
            :server
            :title
            :url
            :action))

(defn insert [action old-id new-id doi server title url date]
  (k/insert event (k/values {:action action :old-id old-id :new-id new-id :doi doi :inserted (coerce/to-sql-date date) :server server :title title :url url})))

