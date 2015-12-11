(ns baleen.server
  (:require [baleen.state :as state]
            [baleen.database :as db]
            [baleen.events :as events])
  (:require [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [clj-time.format :as f])
  (:require [compojure.core :refer [context defroutes GET ANY POST]]
            [compojure.handler :as handler]
            [compojure.route :as route])
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
  (:require [org.httpkit.server :refer [with-channel on-close on-receive send! run-server]])
  (:require [heartbeat.core :refer [def-service-check]]
            [heartbeat.ring :refer [wrap-heartbeat]]))

(def-service-check :mysql (fn [] (db/heartbeat)))
(def-service-check :events (fn [] (events/ok)))

(selmer.parser/cache-off!)
   
; Just serve up a blank page with JavaScript to pick up from event-types-socket.
(defresource home
  []
  :available-media-types ["text/html"] 
  :handle-ok (fn [ctx]
               (render-file "templates/home.html" {:source @state/source})))

(defn event-websocket
  [request]
   (with-channel request output-channel
    (events/register-listener output-channel)
    
    (on-close output-channel
              (fn [status]
                (events/unregister-listener output-channel)))
    (on-receive output-channel
              (fn [data]))))

(defresource status
  []
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx]
                  (json/write-str {:backlog (count state/input-buffer)
                                   :backlog-limit state/input-buffer-size
                                   :num-tied-up-workers @state/num-tied-up-workers
                                   :num-running-workers @state/num-running-workers
                                   :subscribers (count @state/broadcast-channels)
                                   :most-recent-input (when-let [x @state/most-recent-input] (str x))
                                   :most-recent-citation (when-let [x @state/most-recent-citation] (str x))
                                   :num-workers (:num-workers @state/source)
                                   :input-history @state/input-count-buckets
                                   :processed-history @state/processed-count-buckets
                                   :citation-history @state/citation-count-buckets
                                   :recent-events (> (apply + (take 10 @state/input-count-buckets)) 0)})))

(defresource events
  []
  :available-media-types ["application/json"]
  :handle-ok (fn [ctx]
    (let [start-id (when-let [id (get-in ctx [:request :params :start])] (Integer/parseInt id))
          events (events/get-citations-page start-id)
          exported-events (map (fn [{id :id 
                            event-key :event-key
                            doi :doi
                            date :date
                            url :url
                            action :doi}]
                        ((:export-f @state/source) id event-key doi (str date) url action)) events)
          next-offset (-> events last :id)]

      (json/write-str {:events exported-events
                       :next-offset next-offset}))))

(defroutes app-routes
  (GET "/" [] (home))
  (GET "/status" [] (status))
  (GET ["/socket/events"] [] event-websocket)
  (GET ["/events"] [] (events))
  (route/resources "/"))

(def app
  (-> app-routes
      handler/site
      (wrap-heartbeat)))

(defn start []
  (reset! state/server (run-server #'app {:port (:port config)})))