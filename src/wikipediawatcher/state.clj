(ns wikipediawatcher.state
  (:require [clojure.core.async :refer [chan dropping-buffer]]))

; With 20 workers, the size of the backlog tends to hang around the zero mark.
; 500 for extreme hiccoughs.
(def num-workers 20)
(def channel-size 500)

(def changes-buffer (dropping-buffer channel-size))
(def changes (chan changes-buffer))

(def broadcast-channels (atom #{}))

(def most-recent-event (atom nil))
(def most-recent-citation (atom nil))
