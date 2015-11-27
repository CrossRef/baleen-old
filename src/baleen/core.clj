(ns baleen.core
  (:require [baleen.state :as state]
            [baleen.sources.wikimedia-dois :as wikimedia-dois]
            [baleen.sources.gnip-dois :as gnip-dois]
            [baleen.server :as server]
            [baleen.events :as events])
  (:require [crossref.util.config :refer [config]])
  (:require [baleen.util :as util])
  (:require [clojure.tools.logging :refer [error info]]))

(def sources
  {:wikimedia-dois {:vocab {:title "Wikipedia DOI citation live stream"
                       :input-count-label "Wikipedia edits"
                       :citation-count-label "DOI citation events"}

               ; With 20 workers, the size of the backlog tends to hang around the zero mark.
               ; But having some headroom can't hurt.
               :num-workers 50

               :input-bucket-time 5000
               :citation-bucket-time 300000

               :num-input-buckets 200
               :num-citation-buckets 200

               :start-f wikimedia-dois/start
               :export-f wikimedia-dois/export
               :process-f wikimedia-dois/process

               :watchdog-time 10000
               :restart-f wikimedia-dois/restart}

   :gnip-dois {:vocab {:title "Tweets mentioning DOIs live stream"
                  :input-count-label "Tweets"
                  :citation-count-label "DOI mentions"}
          :num-workers 50

          :input-bucket-time 5000
          :citation-bucket-time 300000

          :num-input-buckets 200
          :num-citation-buckets 200

          :start-f gnip-dois/start
          :export-f gnip-dois/export
          :process-f gnip-dois/process

          :watchdog-time 10000
          :restart-f gnip-dois/restart}})

(defn -main
  [& args]
  (when-not (sources (:enabled-source config))
    (error "Didn't recognise source" (:enabled-source config)))

  (info "Using " (:enabled-source config))
  (reset! state/source (get sources (:enabled-source config)))
  (info "Starting source")
  (events/boot)
  (info "Starting server")
  (server/start)
  (info "Running"))