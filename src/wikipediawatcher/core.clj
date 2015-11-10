(ns wikipediawatcher.core
  (:import [wikipediawatcher Client])
  (:import [java.net URLEncoder])
  (:require [clj-time.core :as clj-time])
  (:require [clojure.data.json :as json]
            [clojure.core.async :refer [chan >!! >! <! go]])
  (:require [wikipediawatcher.database :as db]
            [wikipediawatcher.server :as server]
            [wikipediawatcher.state :as state])
  (:require [crossref.util.config :refer [config]])
  (:require [org.httpkit.client :as http]
            [org.httpkit.server :as http-server])
  (:require [wikipediawatcher.util :as util]))


(defn process-change [worker-id args]
  (let [arg (first args)
        arg-str (.toString arg)
        data (json/read-str (.toString arg))

        server-name (get data "server_name")
        server-url (get data "server_url")
        title (get data "title")
        old-revision (get-in data ["revision" "old"])
        new-revision (get-in data ["revision" "new"])

        date (clj-time/now)

        fetch-url (str "https://" server-name "/w/index.php")
        page-url (str "https://" server-name "/w/index.php?title=" (URLEncoder/encode title "UTF-8"))]

        (reset! state/most-recent-event (clj-time/now))
        (state/inc-event-bucket)

    ; This may not be a revision of a page. Ignore if there isn't revision information.
    (when (and server-url title old-revision new-revision)
      
      (let [
        old-content (http/get fetch-url {:query-params {:title title :oldid old-revision}})
        new-content (http/get fetch-url {:query-params {:title title :oldid new-revision}})

        [added-dois removed-dois now-dois] (util/doi-changes (:body @old-content) (:body @new-content))]

        (when (or (not-empty added-dois) (not-empty removed-dois))
          (state/inc-citation-bucket)
          (reset! state/most-recent-citation (clj-time/now)))

        ; Broadcast this to all listeners.
        (doseq [doi added-dois]
          (let [broadcast-data (json/write-str {:doi doi :url page-url :action "add" :wiki server-name :title title :date (str date)})]
          (doseq [c @state/broadcast-channels]
            (http-server/send! c broadcast-data))))

        (doseq [doi removed-dois]
          (let [broadcast-data (json/write-str {:doi doi :url page-url :action "remove" :wiki server-name :title title :date (str date)})]
          (doseq [c @state/broadcast-channels]
            (http-server/send! c broadcast-data))))

        (doseq [doi added-dois]
          (db/insert "add" old-revision new-revision doi server-name title page-url date))
        

        (doseq [doi removed-dois]
          (db/insert "remove" old-revision new-revision doi server-name title page-url date))))))


(dotimes [worker-id state/num-workers]
  (go 
    (loop []
      (process-change worker-id (<! state/changes))
      (recur))))

(defn callback [type-name args]
  (>!! state/changes args))

(defonce s (atom nil))

(defn -main
  [& args]
  (let [client (new Client callback)]
    (reset! s (http-server/run-server #'server/app {:port (:port config)}))
    (.run client)))
