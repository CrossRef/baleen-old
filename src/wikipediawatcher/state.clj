(ns wikipediawatcher.state
  (:require [wikipediawatcher.database :as db])
  (:require [clojure.core.async :refer [chan dropping-buffer]])
  (:require [clj-time.core :as clj-time])
  (:require [overtone.at-at :as at-at]))

(def my-pool (at-at/mk-pool))

; With 20 workers, the size of the backlog tends to hang around the zero mark.
; 500 for extreme hiccoughs.
(def num-workers 20)
(def channel-size 500)

(def changes-buffer (dropping-buffer channel-size))
(def changes (chan changes-buffer))

(def broadcast-channels (atom #{}))

(def most-recent-event (atom nil))
(def most-recent-citation (atom nil))

(def event-bucket-time 5000)
(def citation-bucket-time 300000)

(def num-event-buckets 200)
(def num-citation-buckets 200)

(def citation-buckets (atom (apply list (repeat num-citation-buckets 0))))
(def event-buckets (atom (apply list (repeat num-event-buckets 0))))

(defn shift-bucket [buckets]
  (conj (drop-last buckets) 0))

(defn inc-bucket [buckets]
  (conj (rest buckets) (inc (first buckets)) ))

(at-at/every event-bucket-time #(swap! event-buckets shift-bucket) my-pool)
(at-at/every citation-bucket-time #(swap! citation-buckets shift-bucket) my-pool)

(defn inc-citation-bucket [] (swap! citation-buckets inc-bucket))
(defn inc-event-bucket [] (swap! event-buckets inc-bucket))

(defn citation-history
  "Generate a seq of pairs of time range to populate the initial citation buckets state."
  []
  (let [now (clj-time/now)
        points (map #(clj-time/minus now (clj-time/millis (* citation-bucket-time %))) (range 0 (inc num-citation-buckets)))
        pairs (partition 2 1 points)
        counts (apply list (map db/get-citations-for-period pairs))]
    counts))


; On load populate with previous data, if there is any.
(reset! citation-buckets (citation-history))

