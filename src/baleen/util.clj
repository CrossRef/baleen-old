(ns baleen.util
  (:import [java.net URLDecoder URL MalformedURLException])
  (:require [net.cgrand.enlive-html :as html])
  (:require [clojure.string :as string]))
      
(defn href-to-url [text]
  "Convert an href from an <a>, as found in HTML, to a URL. Accepts protocol-relative URLs."
  (when text
    ; If we get non-URL things, like fragments, skip.
    (try 
    ; These will be found in a well-deliniated URL, so we can take the rest of the link.
    (when-let [url-text (cond
                    (.startsWith text "//") (str "http:" text)
                    (.startsWith text "http:") text
                    (.startsWith text "https:") text
                    ; Ignore relative URLs, as they can't be DOIs or publisher links.
                    :default nil)]
      (new URL (URLDecoder/decode url-text "UTF-8")))
    (catch MalformedURLException e)
    (catch IllegalArgumentException e))))
    
(defn is-doi-url?
  "If the URL is a DOI, return the DOI as a string."
  [url-string]

  (let [url (href-to-url url-string)]
    (when url 
      (let [host (.getHost url)
            url-path (.getPath url)
            ; Drop leading slash.
            url-path (when-not (string/blank? url-path) (subs url-path 1))
            likely-doi (and host
                            url-path
                            (.contains host "doi.org")
                            (.startsWith url-path "10."))]
        (when likely-doi url-path)))))
    
(defn extract-a-hrefs-from-html [input]
    (let [links (html/select (html/html-snippet input) [:a])
          hrefs (keep #(-> % :attrs :href) links)]
      (set hrefs)))

(defn text-fragments-from-html [input]
  (string/join " "
    (-> input
    (html/html-snippet)
    (html/select [:body html/text-node])
    (html/transform [:script] nil)
    (html/texts))))

(defn remove-all
  "Remove a sequence of strings from a string."
  [text dois]
  (reduce (fn [text doi]          
            (.replace text doi "")) text dois))
