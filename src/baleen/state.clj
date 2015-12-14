(ns baleen.state
  (:require [baleen.database :as db])
  (:require [clojure.core.async :refer [chan dropping-buffer]])
  (:require [clj-time.core :as clj-time]
            [clj-time.coerce :as coerce])
  (:require [overtone.at-at :as at-at]))

(defonce server (atom nil))

; Info for selected source. Set at start-up.
(defonce source (atom {}))

; Copy of the config.
(defonce config (atom {}))

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
(defonce processed-count-buckets (atom (list)))
(defonce citation-count-buckets (atom (list)))

; Reset event ID to the millisecond whenever the server starts up.
; This is incremented for each call. Unless there's an event every millisecond, this should always be fine.
(defonce next-event-id (atom (coerce/to-long (clj-time/now))))

(defonce at-at-pool (at-at/mk-pool))