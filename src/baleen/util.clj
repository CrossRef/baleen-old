(ns baleen.util
  (:import [java.net URLDecoder URL MalformedURLException])
  (:require [net.cgrand.enlive-html :as html])
  (:require [clojure.string :as string]))

(defn extract-doi-from-url [text]
  "Convert a link, as found in HTML, to a DOI. Accepts protocol-relative URLs."
  (when text
    ; If we get non-URL things, like fragments, skip.
    (try 
    ; These will be found in a well-deliniated URL, so we can take the rest of the link.
    (when-let [url-text (cond
                    (.startsWith text "//") (str "http:" text)
                    (.startsWith text "http:") text
                    (.startsWith text "https:") text
                    ; relative URLs can't be DOIs. ignore.
                    :default nil)]
      (let [url (new URL (URLDecoder/decode url-text "UTF-8"))
            host (.getHost url)
            url-path (.getPath url)
            url-path (when-not (string/blank? url-path) (subs url-path 1))
            likely-doi (and (.contains host "doi.org")
                            (.startsWith url-path "10."))]
      
      (when likely-doi url-path)))

    (catch MalformedURLException e)

    (catch IllegalArgumentException e
      (locking *out* (prn "MALFORMED" (str e)))))))

(defn extract-links-from-html [input]
    (let [links (html/select (html/html-snippet input) [:a])]
      (set links)))

(defn filter-doi-links [links]
  (let [doi-links (keep #(-> % :attrs :href extract-doi-from-url) links)]
    (set doi-links)))

