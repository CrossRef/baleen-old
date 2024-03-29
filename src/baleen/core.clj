(ns baleen.core
  (:require [baleen.state :as state]
            [baleen.sources.wikimedia-dois :as wikimedia-dois]
            [baleen.sources.wikimedia-restbase-dois :as wikimedia-restbase-dois]
            [baleen.sources.gnip-dois :as gnip-dois]
            [baleen.server :as server]
            [baleen.events :as events])
  (:require [crossref.util.config :refer [config]])
  (:require [baleen.util :as util])
  (:require [clojure.tools.logging :refer [error info]]))

(def sources
  ; Exeperimental, uses Wikimedia RESTBase API.
  {:wikimedia-restbase-dois {:vocab {:title "Wikipedia DOI citation live stream"
                       :input-count-label "Wikipedia edits"
                       :citation-count-label "DOI citation events"}

               :input-bucket-time 5000
               :citation-bucket-time 300000

               :num-input-buckets 100
               :num-citation-buckets 100

               :start-f wikimedia-restbase-dois/start
               :boot-f wikimedia-restbase-dois/boot
               :export-f wikimedia-restbase-dois/export
               :process-f wikimedia-restbase-dois/process

               :watchdog-time 10000
               :restart-f wikimedia-restbase-dois/restart

               ; It doesn't make sense to log these inputs as the ratio of inputs to citiations is about 0.005
               :log-inputs false}

  ; Uses Wikimedia public pages.
  :wikimedia-dois {:vocab {:title "Wikipedia DOI citation live stream"
                       :input-count-label "Wikipedia edits"
                       :citation-count-label "DOI citation events"}

               :input-bucket-time 5000
               :citation-bucket-time 300000

               :num-input-buckets 100
               :num-citation-buckets 100

               :start-f wikimedia-dois/start
               :boot-f wikimedia-dois/boot
               :export-f wikimedia-dois/export
               :process-f wikimedia-dois/process

               :watchdog-time 10000
               :restart-f wikimedia-dois/restart

               ; It doesn't make sense to log these inputs as the ratio of inputs to citiations is about 0.005
               :log-inputs false}

   :gnip-dois {:vocab {:title "Tweets mentioning DOIs live stream"
                  :input-count-label "Tweets"
                  :citation-count-label "DOI mentions"}

          :input-bucket-time 5000
          :citation-bucket-time 300000

          :num-input-buckets 100
          :num-citation-buckets 100

          :boot-f gnip-dois/boot
          :start-f gnip-dois/start
          :export-f gnip-dois/export
          :process-f gnip-dois/process

          :watchdog-time (* 1000 60 60) ; 1hr
          :restart-f gnip-dois/restart

          ; Log inputs for later analysis. Ratio of input events to citations should be near to 1.
          :log-inputs true}})

(defn run []
  (info "Starting server")
  ; Allow server to run without colleting, e.g. to show historical data.
  (if (:collect config)
    (events/run)
    (info "Not collecting events due to config"))
  (server/start)
  (info "Running"))

(defn -main
  [& args]
  (when-not (sources (:enabled-source config))
    (error "Didn't recognise source" (:enabled-source config)))

  (info "Using " (:enabled-source config))
  (reset! state/source (get sources (:enabled-source config)))
  (reset! state/config config)

  (info "Starting source")
  (events/boot)

  (condp = (first args)
    "reprocess" (events/reprocess)
    "run" (run)))