(ns baleen.sources.gnip
  (:require [clj-time.core :as clj-time])
  (:require [clojure.data.json :as json])
  (:require [baleen.database :as db]
            [baleen.server :as server]
            [baleen.state :as state]
            [baleen.events :as events])
  (:require [crossref.util.config :refer [config]])
  (:require [org.httpkit.client :as http]
            [org.httpkit.server :as http-server])
  (:require [baleen.util :as util])
  (:require [robert.bruce :refer [try-try-again]])
  (:require [clojure.tools.logging :refer [error info]]
            [clojure.set :refer [difference]])
  (:import [baleen StreamingGnipConnection]))

(defn extract-doi [input]
  ; TODO
  nil)

(defn process [worker-id args]
  (let [response (first args)
        data (json/read-str response)
        body (get-in data ["body"])
        links (get-in data [""])
        doi (extract-doi args)
        date (clj-time/now)
        url ""
        tweet-id ""
        tweet-text ""
        event-key (json/write-str [tweet-id tweet-text doi])]
  (events/fire-citation event-key doi date url "tweet")))


(defn export [id event-key doi date url action]
  (let [[tweet-id tweet-text doi] (json/read-str event-key)]
  {:id id
   :input-container-title "Tweet"
   :date date
   :doi doi
   :title tweet-text
   :url url
   :pretty-url url
   :action-url url
   :action "tweet"}))

(def connection (atom nil))

(defn callback [input-line]
  (prn input-line))

(defn start []
  (let [new-connection (new StreamingGnipConnection
                         (-> config :source-config :gnip :username)
                         (-> config :source-config :gnip :password)
                         (-> config :source-config :gnip :url)
                         callback)]
    (reset! connection new-connection)))

(defn restart [])