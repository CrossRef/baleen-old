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

(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread throwable]
      (error (.getMessage throwable))
      (.printStackTrace throwable)
      (System/exit 1))))


(defn get-citations-page [from-id]
  ; Don't retrieve flagged entries, they're not intended for viewing.
  (if from-id
    (k/select db/citation-event (k/where (< :id from-id)) (k/where {:flagged false}) (k/order :date :DESC) (k/limit page-size))
    (k/select db/citation-event (k/order :date :DESC) (k/where {:flagged false}) (k/limit page-size))))

(defn- citation-count-for-period [[end start]]
  (->
    (k/select db/citation-event (k/aggregate (count :*) :cnt)
      (k/where {:flagged false})
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
  ; Conj will create a lazy seq and blow up the stack eventually. Realize the bucket list each time.
  (apply list (conj (drop-last buckets) 0)))

(defn- inc-bucket [buckets]
  (conj (rest buckets) (inc (first buckets))))

(defn fire-input
  "Fire an incoming event."
  [event]
  (let [instance-name (-> @state/config :instance-name)
        input-event-id (str instance-name ":" (swap! state/next-event-id inc))]
    (>!! state/input-queue [event input-event-id])
    (reset! state/most-recent-input (clj-time/now))
    ; Increment the current count bucket.
    (swap! state/input-count-buckets inc-bucket)))

(defn fire-citation
  "Fire a citation event"
  [input-event-id event-key doi date url action flagged]

  ; We expect duplicates when running redundant instances. Ignore these errors.
  (try 
    (k/insert db/citation-event (k/values {:input-event-id input-event-id :event-key event-key :doi doi :date date :url url :action action :flagged flagged}))
    (catch com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException _ nil))

  ; If this is a flag it's not destined to be shown for users (and won't be retrieved through /events either).
  (when-not flagged

    ; Citation counts will come in more or less sequentially.
    ; If they aren't, it'll be because of high throughput so it won't matter if this is a few ms out of date anyway;
    (reset! state/most-recent-citation date)
    (swap! state/citation-count-buckets inc-bucket)

    ; Broadcast to all websocket listeners.
    (let [exported (json/write-str ((:export-f @state/source) 0 event-key doi (str date) url action))]
      (doseq [c @state/broadcast-channels]
        (http-server/send! c exported)))))

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

(defn ok []
  ; One event in the last 5 bucketsworth.
  (> (apply + (take 5 @state/input-count-buckets)) 0))

(defn watchdog []
    (when-not (ok) (error "Watchdog failed")
      (prn "Watchdog" (ok))
      ((:restart-f @state/source))))

(defn boot []
  {:pre [@state/source]}
  (let [source @state/source
        process-f (:process-f source)]

  (info "Input buckets size " (:num-input-buckets source))
  ; Start with empty event buckets. They'll fill up soon enough.
  (reset! state/input-count-buckets (apply list (repeat (:num-input-buckets source) 0)))
  (reset! state/processed-count-buckets (apply list (repeat (:num-input-buckets source) 0)))

  ; On load populate with previous data, if there is any.
  (info "Citation buckets size " (:num-citation-buckets source))
  (reset! state/citation-count-buckets (citation-history))

  ; Schedule the buckets to shift.
  (info "Set up bucket timers")
  (at-at/every (:input-bucket-time source) #(swap! state/input-count-buckets shift-bucket) state/at-at-pool)
  (at-at/every (:input-bucket-time source) #(swap! state/processed-count-buckets shift-bucket) state/at-at-pool)
  (at-at/every (:citation-bucket-time source) #(swap! state/citation-count-buckets shift-bucket) state/at-at-pool)

  ; Start delayed to let things populate.
  (info "Delay watchdog")
  ; One minute to allow things to connect plus the watchdog wait time.
  (at-at/after (+ (* 1000 60) (:watchdog-time source))
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
        (swap! state/num-running-workers inc)
        (loop []
          ; Don't allow an exception to crash the worker.
          ; There are watchdogs and logging to take care of reporting and restarting.
          (let [[event input-event-id] (<! state/input-queue)]
            (swap! state/num-tied-up-workers inc)
            (try 
              (when (:log-inputs @state/source)
                (info "INSERT" {:event-id input-event-id :content (json/write-str event) :date (clj-time/now)})
                ; The event may be in any format. JSONize it. For the extant sources, this means that a JSON-encoded string is encoded again.
                (k/insert db/input-event (k/values {:event-id input-event-id :content (json/write-str event) :date (clj-time/now)})))

              (process-f worker-id input-event-id event)
              (catch Exception e (error (str "Exception " e))))
              (swap! state/num-tied-up-workers dec)
              (swap! state/processed-count-buckets inc-bucket)
          (recur)))
          ; The loop should never end, so this point should never be reached.
          (swap! state/num-running-workers dec)))))

(defn reprocess
  "Reprocess the logged data from all the logged inputs for the currently selected source."
  []
  (let [input-events (k/select db/input-event)]
    (doseq [input-event input-events]
      
      (let [event-id (:id input-event)
            worker-id 0
            args (json/read-str (:content input-event))]

        (info "Reprocess" event-id args)


      (k/delete db/citation-event (k/where {:input-event-id (:id input-event)}))
      ((:process-f @state/source) 0 event-id args)))))
