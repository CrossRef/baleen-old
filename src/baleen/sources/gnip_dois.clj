(ns baleen.sources.gnip-dois
  (:require [clj-time.core :as clj-time])
  (:require [clojure.data.json :as json])
  (:require [baleen.database :as db]
            [baleen.server :as server]
            [baleen.state :as state]
            [baleen.events :as events])
  (:require [clojure.core.async :refer [go]])
  (:require [crossref.util.config :refer [config]])
  (:require [org.httpkit.client :as http]
            [org.httpkit.server :as http-server])
  (:require [baleen.util :as util])
  (:require [robert.bruce :refer [try-try-again]])
  (:require [clojure.tools.logging :refer [error info]]
            [clojure.set :refer [difference]])
  (:import [baleen StreamingGnipConnection]))

(def verb-names
  {"post" "tweeted"
   "share" "retweeted"})

(defn extract-info
  "Take data structure from Gnip input and extract info."
  [input-line]
  (let [data (json/read-str input-line)
        id (get-in data ["id"])
        tweet-url (get-in data ["object" "link"])
        body (get-in data ["body"])
        verb (get-in data ["verb"])
        posted-time (get-in data ["postedTime"])
        ; Include both the expanded url and the original URL, one might be a DOI, or match our domain list.
        urls (set (mapcat (fn [url-structure] [(get url-structure "url") (get url-structure "expanded_url")])(get-in data ["gnip" "urls"])))]
    {:tweet-id id
     :tweet-url tweet-url
     :body body
     :verb verb
     :posted-time posted-time
     :urls urls}))

(defn extract-dois-from-urls [urls]
  ; TODO implement. For trial we want to just pass through the URL that was found.
  ; Optionally check against the list of member domains to filer only those that match.
  (set urls))

(defn process [worker-id event-id args]
  (let [{tweet-id :tweet-id tweet-url :tweet-url body :body verb :very posted-time :posted-time urls :urls} (extract-info args)
        dois (extract-dois-from-urls urls)]
    (doseq [doi dois]
      (let [event-key (json/write-str [tweet-id doi verb body])]
        (events/fire-citation event-key doi posted-time tweet-url verb)))))

(defn export [id event-key doi date url action]
  (let [[tweet-id doi verb tweet-text] (json/read-str event-key)
        exported-verb (get verb-names verb verb)]
  {:id id
   :input-container-title "Tweet"
   :date date
   :doi doi
   :title tweet-text
   :url url
   :pretty-url url
   :action-url url
   :action exported-verb}))

(def connection (atom nil))

(defn callback [input-line]
  (info (str "Callback:" input-line))
  (when-not (clojure.string/blank? input-line)
    (info "Callback wasn't empty:" input-line)
    (events/fire-input input-line)))

(defn start []
  (info "Starting GNIP")
  (let [new-connection (new StreamingGnipConnection
                         (-> config :source-config :gnip :username)
                         (-> config :source-config :gnip :password)
                         (-> config :source-config :gnip :url)
                         callback)]
    (info "GNIP connection:" new-connection)
    (reset! connection new-connection)
    (go (.run new-connection))))

(defn restart []


  )