(ns baleen.core
  (:require [baleen.state :as state]
            [baleen.wikimedia.wikimedia :as wikimedia]
            [baleen.server :as server])
  (:require [crossref.util.config :refer [config]])
  (:require [baleen.util :as util])
  (:require [clojure.tools.logging :refer [error info]]))

(def available-sources #{:wikimedia})

(defn -main
  [& args]
  (when-not available-sources (:enabled-source config)
    (error "Didn't recognise source" (:enabled-source config)))

  (server/start)

  (condp = (:enabled-source config)
    :wikimedia (wikimedia/run)
    :default (error "Didn't start a source" (:enabled-source config))))