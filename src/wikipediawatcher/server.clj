(ns wikipediawatcher.server
  (:require [wikipediawatcher.state :as state]
            [wikipediawatcher.database :as db])
  (:require [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [clj-time.format :as f])
  (:require 
    [compojure.core :refer [context defroutes GET ANY POST]]
           [compojure.handler :as handler]
           [compojure.route :as route]
  )
  (:require [ring.util.response :refer [redirect]])
  (:require [liberator.core :refer [defresource resource]]
            [liberator.representation :refer [ring-response]])
  (:require [selmer.parser :refer [render-file cache-off!]]
            [selmer.filters :refer [add-filter!]])
  (:require [clojure.data.json :as json])
  (:require [crossref.util.doi :as crdoi]
            [crossref.util.config :refer [config]])
  (:require [clojure.walk :refer [prewalk]])
  (:require [clojure.core.async :as async :refer [<! <!! >!! >! go chan]])
  (:require [org.httpkit.server :refer [with-channel on-close on-receive send! run-server]]))


(selmer.parser/cache-off!)
            
; Just serve up a blank page with JavaScript to pick up from event-types-socket.
(defresource home
  []
  :available-media-types ["text/html"] 
  :handle-ok (fn [ctx]
               (let []
                 (render-file "templates/home.html" {}))))

(defn register-listener [listener-chan]
  (swap! state/broadcast-channels conj listener-chan))

(defn unregister-listener [listener-chan]
  (swap! state/broadcast-channels disj listener-chan))

(defn event-websocket
  [request]
   (with-channel request output-channel
    (register-listener output-channel)
    
    (on-close output-channel
              (fn [status]
                (unregister-listener output-channel)))
    (on-receive output-channel
              (fn [data]))))

(defresource status
  []
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx]
             
                  (json/write-str {:backlog (count state/changes-buffer)
                                   :backlog-limit state/channel-size
                                   :subscribers (count @state/broadcast-channels)
                                   :most-recent-event (when-let [x @state/most-recent-event] (str x))
                                   :most-recent-citation (when-let [x @state/most-recent-citation] (str x))
                                   :num-workers state/num-workers
                                   :event-history @state/event-buckets
                                   :citation-history @state/citation-buckets
                                   :recent-events (> (apply + (take 10 @state/event-buckets)) 0)})))

(defresource events
  []
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx]
    (let [start-id (when-let [id (get-in ctx [:request :params :start])] (Integer/parseInt id))
          events (db/get-events-page start-id)
          events (map (fn [event] (assoc event :date (str (:date event))
                                               :wiki (:server event))

            ) events)
          next-offset (-> events last :id)]

      (json/write-str {:events events
                       :next-offset next-offset}))))

(defroutes app-routes
  (GET "/" [] (home))
  (GET "/status" [] (status))
  (GET ["/socket/events"] [] event-websocket)
  (GET ["/events"] [] (events))
  (route/resources "/"))

(def app
  (-> app-routes
      handler/site))
