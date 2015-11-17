(ns baleen.events
  (:require [baleen.database :as db]
            [baleen.state :as state])
  (:require [crossref.util.config :refer [config]])
  (:require [korma.db :as kdb])
  (:require [korma.core :as k])
  (:require [korma.db :refer [mysql with-db defdb]])
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]])
  (:require [clj-time.coerce :as coerce]
            [clj-time.core :as clj-time]
            [overtone.at-at :as at-at])
  (:require [clojure.core.async :refer [chan >!! >! <! go]]
            [clojure.data.json :as json]
            [clojure.tools.logging :refer [error info]])
  (:require [org.httpkit.server :as http-server]))

(def page-size 20)

(defn get-citations-page [from-id]
  (if from-id
    (k/select db/citation-event (k/where (< :id from-id)) (k/order :id :DESC) (k/limit page-size))
    (k/select db/citation-event (k/order :id :DESC) (k/limit page-size))))

(defn- citation-count-for-period [[end start]]
  (->
    (k/select db/citation-event (k/aggregate (count :*) :cnt)
      (k/where (>= :date (coerce/to-sql-time start)))
      (k/where (< :date (coerce/to-sql-time end))))
    first
    :cnt))

(defn- citation-history
  "Generate a citation-count-buckets from the database. Used to populate at startup."
  []
  (let [now (clj-time/now)
        points (map #(clj-time/minus now (clj-time/millis (* (:citation-bucket-time @state/source) %))) (range 0 (inc (:num-citation-buckets @state/source))))
        pairs (partition 2 1 points)
        counts (apply list (map citation-count-for-period pairs))]
    counts))


(defn- shift-bucket [buckets]
  (conj (drop-last buckets) 0))

(defn- inc-bucket [buckets]
  (conj (rest buckets) (inc (first buckets))))

(defn fire-input
  "Fire an incoming event."
  [event]
  (>!! state/input-queue event)
  (reset! state/most-recent-input (clj-time/now))
  ; Increment the current count bucket.
  (swap! state/input-count-buckets inc-bucket))

(defn fire-citation
  "Fire a citation event"
  [event-key doi date url action]

  ; Citation counts will come in more or less sequentially.
  ;If they aren't, it'll be because of high throughput so it won't matter if this is a few ms out of date anyway;
  (reset! state/most-recent-citation date)
  (swap! state/citation-count-buckets inc-bucket)

  ; We expect duplicates when running redundant instances. Ignore these errors.
  (try 
    (k/insert db/citation-event (k/values {:event-key event-key :doi doi :date date :url url :action action}))
    (catch com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException _ nil))

  ; Broadcast to all websocket listeners.
  (let [exported (json/write-str ((:export-f @state/source) event-key doi (str date) url action))]
    (doseq [c @state/broadcast-channels]
      (http-server/send! c exported))))

(defn register-listener
  "Register a websocket listener."
  [listener-chan]
  (info "Register websocket listener" (str listener-chan) "now" (inc (count @state/broadcast-channels)))
  (swap! state/broadcast-channels conj listener-chan))

(defn unregister-listener
  "Unregister a websocket listener."
  [listener-chan]
  (info "Unregister websocket listener" (str listener-chan) "now" (dec (count @state/broadcast-channels)))
  (swap! state/broadcast-channels disj listener-chan))

(defn watchdog []
  ; One event in the last 5 bucketsworth.
  (let [ok (> (apply + (take 5 @state/input-count-buckets)) 0)]
    (info "Watchdog" ok)
    (when-not ok (error "Watchdog failed")
      ((:restart-f @state/source)))))

(defn boot []
  {:pre @state/source}
  (let [source @state/source
        process-f (:process-f source)]

  (info "Input buckets size " (:num-input-buckets source))
  ; Start with empty event buckets. They'll fill up soon enough.
  (reset! state/input-count-buckets (apply list (repeat (:num-input-buckets source) 0)))

  ; On load populate with previous data, if there is any.
  (info "Citation buckets size " (:num-citation-buckets source))
  (reset! state/citation-count-buckets (citation-history))

  ; Schedule the buckets to shift.
  (at-at/every (:input-bucket-time source) #(swap! state/input-count-buckets shift-bucket) state/at-at-pool)
  (at-at/every (:citation-bucket-time source) #(swap! state/citation-count-buckets shift-bucket) state/at-at-pool)

  ; Start delayed to let things populate.
  (at-at/after 30000
    (fn []
        (info "Start watchdog time" (:watchdog-time source))
        (at-at/every (:watchdog-time source) watchdog state/at-at-pool)) state/at-at-pool)

  ; Start source ingesting into queue.
  (info "Start source")
  ((source :start-f))


  ; Start workers processing queue.
  (info "Start " (:num-workers source) " workers")
    (dotimes [worker-id (:num-workers source)]
      (go 
        (loop []
          (process-f worker-id (<! state/input-queue))
          (recur))))))
