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
  (:import [baleen StreamingGnipConnection]
           [java.net URL]))

(def verb-names
  {"post" "tweeted"
   "share" "retweeted"})

; Immediately reject these domains. They often show up once per tweet. Because GNIP unrolls URLs, they're never useful.
(def ignore-domains #{"t.co" "twitter.com" "goo.gl" "google.com" "shar.es" "bit.ly" "wp.me" "buff.ly" "ow.ly" "fb.me" "lnkd.in" "bitly.com" "youtube.com"})

; These domains are always interesting.
(def always-domains #{"doi.org" "dx.doi.org"})

(def guess-doi-server (:guess-doi-server config))

; Set of all member domains.
(def member-domains (atom #{}))
(defn fetch-member-domains []
  (->> config :member-domains-server
       http/get deref
       :body json/read-str
       set
       (clojure.set/union always-domains)
       (#(clojure.set/difference % ignore-domains))
       (reset! member-domains)))

(defn interested-in-url?
  "Should a URL be followed? Keep only known member domains and DOIs."
  [url]
  (try
    (@member-domains (.getHost (new URL url)))
    ; We may get non-urls conceivably. Ignore these.
    (catch Exception _ true)))

(defn extract-dois
  "Given a url or two or some text, extract and verify the DOI. Uses the 'DOI destinations' service."
  [strings]
  (keep (fn [string]
    (try-try-again {:sleep 500 :tries 2}
      (fn []
        (let [response @(http/get guess-doi-server {:query-params {:q string}})]
          (when (= 200 (:status response))
            (:body response)))))) strings))

(defn extract-info
  "Take data structure from Gnip input and extract info."
  [input-line]
  (let [data (json/read-str input-line)
        id (get-in data ["id"])
        tweet-url (get-in data ["object" "link"])
        body (get-in data ["body"])
        verb (get-in data ["verb"])
        posted-time (get-in data ["postedTime"])
        
        ; Get DOI from URLs.
        ; Include both the expanded url and the original URL, one might be a DOI, or match our domain list.
        ; Look in both the value-added `gnip` and the `twitter_urls` section (latter looks more promising).
        gnip-urls (mapcat (fn [url-structure]
                            [(get url-structure "url") (get url-structure "expanded_url")]) (get-in data ["gnip" "urls"]))

        twitter-urls (mapcat (fn [url-structure]
                            [(get url-structure "url") (get url-structure "expanded_url")]) (get-in data ["twitter_entities" "urls"]))

        potential-urls (concat gnip-urls twitter-urls)
        urls (set (filter interested-in-url? potential-urls))
        url-dois (set (extract-dois urls))

        ; Get DOI from text. The 'DOI destination' service handles the whole process.
        text-dois (set (extract-dois [body]))

        dois (set (concat url-dois text-dois))
        ]

    {:tweet-id id
     :tweet-url tweet-url
     :body body
     :verb verb
     :posted-time posted-time
     :urls urls
     :dois dois}))

(defn process [worker-id event-id args]
  (let [{tweet-id :tweet-id tweet-url :tweet-url body :body verb :verb posted-time :posted-time urls :urls dois :dois} (extract-info args)]
    (doseq [doi dois]
      (let [event-key (json/write-str [tweet-id doi verb body])]
        (events/fire-citation event-id event-key doi posted-time tweet-url verb false)))))

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

(defn boot
  "Always called to set things up."
  []
  (fetch-member-domains))

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