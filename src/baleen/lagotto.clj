(ns baleen.lagotto
  (:require [clojure.data.json :as json])
  (:require [baleen.database :as db])
  (:require [crossref.util.config :refer [config]])
  (:require [korma.db :as kdb])
  (:require [korma.core :as k])
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]])
  (:require [clj-time.coerce :as coerce]
            [clj-time.core :as clj-time])
  (:require [org.httpkit.client :as http-client]))


(defn send-triple
  [subject relation-type object event-key action source-id]
  {:pre [subject relation-type object event-key (#{:add :delete} action) source-id]}
  (let [source-token (get-in config [:lagotto :source-token])
        auth-token (get-in config [:lagotto :auth-token])
        payload (condp = action
          :add {:deposit {:uuid event-key
                          :message_action "add"
                          :source_token source-token
                          :subj_id subject
                          :obj_id object
                          :relation_type_id relation-type
                          :source_id source-id}}
          :delete {:deposit {:uuid event-key
                            :message_action "delete"
                            :source_token source-token
                            :subj_id subject
                            :obj_id object
                            :relation_type_id relation-type
                            :source_id source-id}}
          nil)]

    (when payload
      (http-client/request 
        {:url (get-in config [:lagotto :push-endpoint])
         :method :post
         :headers {"Authorization" (str "Token token=" auth-token) "Content-Type" "application/json"}
         :body (json/write-str payload)}
      (fn [response]
        (locking *out* (prn "Lagotto response" response))
        (k/update db/citation-event (k/set-fields {:pushed true}) (k/where {:event-key event-key})))))))
