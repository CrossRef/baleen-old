(ns baleen.state
  (:require [baleen.database :as db])
  (:require [clojure.core.async :refer [chan dropping-buffer]])
  (:require [clj-time.core :as clj-time])
  (:require [overtone.at-at :as at-at]))

(defonce server (atom nil))

; Info for selected source. Set at start-up.
(defonce source (atom {}))

; Number of workers who are busy working at this point in time.
(defonce num-tied-up-workers (atom 0))

; Number of workers who have been started but may be idle.
(defonce num-running-workers (atom 0))

(def input-buffer-size 50)

(defonce input-buffer (dropping-buffer input-buffer-size))
(defonce input-queue (chan input-buffer))

(defonce broadcast-channels (atom #{}))

(defonce most-recent-input (atom nil))
(defonce most-recent-citation (atom nil))

(defonce input-count-buckets (atom (list)))
(defonce citation-count-buckets (atom (list)))

(defonce at-at-pool (at-at/mk-pool))