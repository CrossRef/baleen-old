(ns baleen.sources.wikimedia-dois
  (:import [baleen RCStreamLegacyClient])
  (:import [java.net URLEncoder]
            [java.util.logging Logger Level])
  (:require [clj-time.core :as clj-time])
  (:require [clojure.data.json :as json])
  (:require [baleen.database :as db]
            [baleen.server :as server]
            [baleen.state :as state]
            [baleen.events :as events])
  (:require [crossref.util.config :refer [config]])
  (:require [org.httpkit.client :as http]
            [org.httpkit.server :as http-server])
  (:require [baleen.util :as util])
  (:require [robert.bruce :refer [try-try-again]])
  (:require [clojure.tools.logging :refer [error info]]
            [clojure.set :refer [difference]])
  (:require [overtone.at-at :as at-at]))
  
(defn fetch-page-dois
  "Fetch the set of DOIs that are mentioned in the given URL.
  Also flag up the presence of DOIs in text that aren't linked."
  [url revision]
  ; If we've fetched this before it's ready, we get an empty page with this link.
  ; It's differenet for each language, so the only common thing is '/delete'.
  ; Keep trying until we get something.
  (try-try-again {:sleep 5000 :tries 5 :return? :truthy?}
      (fn []

        ; Mediawiki allows serving by revision ID, title is not required.
        ; As some character encoding inconsistencies crop up from time to time in the live stream, this reduces the chance of error. 
        (let [result (http/get url {:query-params {:oldid revision}})

              body (or (:body @result) "")

              hrefs (util/extract-a-hrefs-from-html body)

              ; When the diff isn't available, there's no categorical way to tell. 
              ; Clues include a link containing '/delete'
              has-deleted-link (some (fn [url]
                                       (.contains url "/delete")) hrefs)

              ; And a mention of the revision number in question.
              quotes-revision (.contains body (str revision))

              dois (set (keep util/is-doi-url? hrefs))

              fail (and (empty? dois) has-deleted-link quotes-revision)

              ; Get the body text i.e. only visible stuff, not a href links.
              body-text (util/text-fragments-from-html body)

              ; Remove the DOIs that we already found.
              body-text-without-dois (util/remove-all body-text dois)

              ; As we only want to flag the existence of non-linked DOIs for later investigation,
              ; we only need to capture the prefix.
              unlinked-doi-prefixes (re-seq #"10\.\d\d\d+/" body-text-without-dois)

              num-unlinked-dois (count unlinked-doi-prefixes)]
          (when fail (info "Failed" url revision "retry" ))
          (when-not fail [dois num-unlinked-dois])))))

(defn doi-changes [server-name old-revision new-revision]
  (let [fetch-url (str "https://" server-name "/w/index.php")
        [old-dois _] (fetch-page-dois fetch-url old-revision)
        [new-dois num-unlinked-dois] (fetch-page-dois fetch-url new-revision)
        added-dois (difference new-dois old-dois)
        removed-dois (difference old-dois new-dois)]
  [added-dois removed-dois num-unlinked-dois]))

(defn process [worker-id args]
  (let [arg (first args)
        arg-str (.toString arg)
        data (json/read-str (.toString arg))
        server-name (get data "server_name")
        server-url (get data "server_url")
        title (get data "title")
        old-revision (get-in data ["revision" "old"])
        new-revision (get-in data ["revision" "new"])
        date (clj-time/now)
        event-type (get data "type")]
    ; This may not be a revision of a page. Ignore if there isn't revision information.
    (when (and (= event-type "edit")
               server-url title old-revision new-revision)  
      (let [[added-dois removed-dois num-unlinked-dois] (doi-changes server-name old-revision new-revision)
            fetch-url (str "https://" server-name "/w/index.php")
              ; this method is private but this is better than copy-pasting.
              url (str fetch-url "?" (#'http/query-string {:title title}))]
            
            ; When there are any unlinked DOIs, fire a flagged event. This can be picked up by another tool to investigate.
            ; Can be nil if there was an error in retrieval.
            (when (and num-unlinked-dois (> num-unlinked-dois 0))
              (let [event-key (json/write-str [old-revision new-revision "" title server-name "has-unlinked"])]
                (events/fire-citation event-key "" date url "cite" true)))
              
            (when (or (not-empty added-dois) (not-empty removed-dois))
              (reset! state/most-recent-citation (clj-time/now)))

            ; Broadcast this to all listeners.
            (doseq [doi added-dois]
              (let [event-key (json/write-str [old-revision new-revision doi title server-name "cite"])]
                (events/fire-citation event-key doi date url "cite" false)))

            (doseq [doi removed-dois]
              (let [event-key (json/write-str [old-revision new-revision doi title server-name "uncite"])]
                (events/fire-citation event-key doi date url "uncite" false)))))))

;From https://meta.wikimedia.org/wiki/List_of_Wikipedias#1.2B_articles
(def server-names {
  "en" "English"
  "sv" "Swedish"
  "nl" "Dutch"
  "de" "German"
  "fr" "French"
  "war" "Waray-Waray",
  "ru" "Russian"
  "ceb" "Cebuano"
  "it" "Italian"
  "es" "Spanish"
  "vi" "Vietnamese"
  "pl" "Polish"
  "ja" "Japanese"
  "pt" "Portuguese"
  "zh" "Chinese"
  "uk" "Ukrainian"
  "ca" "Catalan"
  "fa" "Persian"
  "no" "Norwegian (Bokmål)"
  "sh" "Serbo-croatian"
  "fi" "Finnish"
  "ar" "Arabic"
  "id" "Indonesian"
  "cs" "Czech"
  "sr" "Serbian"
  "ro" "Romanian"
  "ko" "Korean"
  "hu" "Hungarian"
  "ms" "Malay"
  "tr" "Turkish"
  "min" "Minangkabau"
  "eo" "Esperanto"
  "kk" "Kazakh"
  "eu" "Basque"
  "sk" "Slovak"
  "da" "Danish"
  "bg" "Bulgarian"
  "he" "Hebrew"
  "lt" "Lithuanian"
  "hy" "Armenian"
  "hr" "Croatian"
  "sl" "Slovenian"
  "et" "Estonian"
  "uz" "Uzbek"
  "gl" "Galician"
  "nn" "Norwegian (Nynorsk)"
  "vo" "Volapük"
  "la" "Latin"
  "simple" "Simple English"
  "el" "Greek"
  "hi" "Hindi"
  "az" "Azerbaijani"
  "th" "Thai"
  "ka" "Georgian"
  "ce" "Chechen"
  "oc" "Occitan"
  "be" "Belarusian"
  "mk" "Macedonian"
  "mg" "Malagasy"
  "new" "Newar"
  "ur" "Urdu"
  "tt" "Tatar"
  "ta" "Tamil"
  "pms" "Piedmontese"
  "cy" "Welsh"
  "tl" "Tagalog"
  "lv" "Latvian"
  "bs" "Bosnian"
  "te" "Telugu"
  "be-tasask" "Belarusian (Taraškievica)"
  "br" "Breton"
  "ht" "Haitian"
  "sq" "Albanian"
  "jv" "Javanese"
  "lb" "Luxembourgish"
  "mr" "Marathi"
  "is" "Icelandic"
  "ml" "Malayalam"
  "zh-yue" "Cantonese"
  "bn" "Bengali"
  "af" "Afrikaans"
  "ba" "Bashkir"
  "ga" "Irish"
  "pnb" "Western Punjabi"
  "cv" "Chuvash"
  "fy" "West Frisian"
  "lmo" "Lombard"
  "tg" "Tajik"
  "sco" "Scots"
  "my" "Burmese"
  "yo" "Yoruba"
  "an" "Aragonese"
  "ky" "Kirghiz"
  "sw" "Swahili"
  "io" "Ido"
  "ne" "Nepali"
  "gu" "Gujarati"
  "scn" "Sicilian"
  "bpy" "Bishnupriya Manipuri"
  "nds" "Low Saxon"
  "ku" "Kurdish"
  "ast" "Asturian"
  "qu" "Quechua"
  "als" "Alemannic"
  "su" "Sundanese"
  "pa" "Punjabi"
  "kn" "Kannada"
  "ckb" "Sorani"
  "ia" "Interlingua"
  "mn" "Mongolian"
  "nap" "Neapolitan"
  "bug" "Buginese"
  "arz" "Egyptian Arabic"
  "bat-smg" "Samogitian"
  "wa" "Walloon"
  "zh-min-nan" "Min Nan"
  "am" "Amharic"
  "map-bms" "Banyumasan"
  "gd" "Scottish Gaelic"
  "yi" "Yiddish"
  "mzn" "Mazandarani"
  "si" "Sinhalese"
  "fo" "Faroese"
  "bar" "Bavarian"
  "vec" "Venetian"
  "nah" "Nahuatl"
  "sah" "Sakha"
  "os" "Ossetian"
  "sa" "Sanskrit"
  "roa-tara" "Tarantino"
  "li" "Limburgish"
  "hsb" "Upper Sorbian"
  "or" "Oriya"
  "pam" "Kapampangan"
  "mrj" "Hill Mari"
  "mhr" "Meadow Mari"
  "se" "Northern Sami"
  "mi" "Maori"
  "ilo" "Ilokano"
  "hif" "Fiji Hindi"
  "bcl" "Central Bicolano"
  "gan" "Gan"
  "rue" "Rusyn"
  "ps" "Pashto"
  "glk" "Gilaki"
  "nds-nl" "Dutch Low Saxon"
  "bo" "Tibetan"
  "vls" "West Flemish"
  "diq" "Zazaki"
  "fiu-vro" "Võro"
  "xmf" "Mingrelian"
  "tk" "Turkmen"
  "gv" "Manx"
  "sc" "Sardinian"
  "co" "Corsican"
  "csb" "Kashubian"
  "hak" "Hakka"
  "km" "Khmer"
  "kv" "Komi"
  "vep" "Vepsian"
  "zea" "Zeelandic"
  "crh" "Crimean Tatar"
  "zh-classical" "Classical Chinese"
  "frr" "North Frisian"
  "eml" "Emilian-Romagnol"
  "ay" "Aymara"
  "stq" "Saterland Frisian"
  "udm" "Udmurt"
  "wuu" "Wu"
  "nrm" "Norman"
  "kw" "Cornish"
  "rm" "Romansh"
  "szl" "Silesian"
  "so" "Somali"
  "koi" "Komi-Permyak"
  "as" "Assamese"
  "lad" "Ladino"
  "mt" "Maltese"
  "fur" "Friulian"
  "dv" "Divehi"
  "gn" "Guarani"
  "dsb" "Lower Sorbian"
  "pcd" "Picard"
  "ie" "Interlingue"
  "sd" "Sindhi"
  "lij" "Ligurian"
  "cbk-zam" "Zamboanga Chavacano"
  "cdo" "Min Dong"
  "ksh" "Ripuarian"
  "ext" "Extremaduran"
  "mwl" "Mirandese"
  "gag" "Gagauz"
  "ang" "Anglo Saxon"
  "ace" "Acehnese"
  "ug" "Uyghur"
  "pi" "Pali"
  "pag" "Pangasinan"
  "nv" "Navajo"
  "frp" "Franco Provençal/Arpitan"
  "lez" "Lezgian"
  "sn" "Shona"
  "kab" "Kabyle"
  "ln" "Lingala"
  "pfl" "Palatinate German"
  "myv" "Erzya"
  "xal" "Kalmyk"
  "krc" "Karachay-Balkar"
  "haw" "Hawaiian"
  "rw" "Kinyarwanda"
  "pdc" "Pennsylvania German"
  "kaa" "Karakalpak"
  "to" "Tongan"
  "kl" "Greenlandic"
  "arc" "Aramaic"
  "nov" "Novial"
  "kbd" "Kabardian Circassian"
  "av" "Avar"
  "bxr" "Buryat (Russia)"
  "lo" "Lao"
  "bjn" "Banjar"
  "ha" "Hausa"
  "tet" "Tetum"
  "tpi" "Tok Pisin"
  "na" "Nauruan"
  "pap" "Papiamentu"
  "lbe" "Lak"
  "jbo" "Lojban"
  "ty" "Tahitian"
  "mdf" "Moksha"
  "roa-rup" "Aromanian"
  "wo" "Wolof"
  "tyv" "Tuvan"
  "ig" "Igbo"
  "srn" "Sranan"
  "nso" "Northern Sotho"
  "kg" "Kongo"
  "ab" "Abkhazian"
  "ltg" "Latgalian"
  "zu" "Zulu"
  "om" "Oromo"
  "chy" "Cheyenne"
  "za" "Zhuang"
  "cu" "Old Church Slavonic"
  "rmy" "Romani"
  "tw" "Twi"
  "tn" "Tswana"
  "chr" "Cherokee"
  "pih" "Norfolk"
  "mai" "Maithili"
  "got" "Gothic"
  "bi" "Bislama"
  "xh" "Xhosa"
  "sm" "Samoan"
  "ss" "Swati"
  "mo" "Moldovan"
  "rn" "Kirundi"
  "ki" "Kikuyu"
  "pnt" "Pontic"
  "bm" "Bambara"
  "iu" "Inuktitut"
  "ee" "Ewe"
  "lg" "Luganda"
  "ts" "Tsonga"
  "fj" "Fijian"
  "ak" "Akan"
  "ik" "Inupiak"
  "st" "Sesotho"
  "sg" "Sango"
  "ff" "Fula"
  "dz" "Dzongkha"
  "ny" "Chichewa"
  "ch" "Chamorro"
  "ti" "Tigrinya"
  "ve" "Venda"
  "ks" "Kashmiri"
  "tum" "Tumbuka"
  "cr" "Cree"
  "ng" "Ndonga"
  "cho" "Choctaw"
  "kj" "Kuanyama"
  "mh" "Marshallese"
  "ho" "Hiri Motu"
  "ii" "Sichuan Yi"
  "aa" "Afar"
  "mus" "Muscogee"
  "hz" "Herero"
  "kr" "Kanuri"})

(defn- server-name [server]
  (let [subdomain (first (.split server "\\."))]
    (str (get server-names subdomain subdomain) " Wikipedia")))


(defn export [id event-key doi date url action]
  (let [[old-revision new-revision doi title server action] (json/read-str event-key)
        pretty-url (str server "/wiki/" title)
        action-url (str "https://" server "/w/index.php?" (#'http/query-string {:title title :type "revision" :oldid old-revision :diff new-revision}))]
  {:id id
   :input-container-title (server-name server)
   :date date
   :doi doi
   :title title
   :url url
   :pretty-url pretty-url
   :action-url action-url
   :action action}))

(defn- callback [type-name args]
  ; Delay event to give a chance for Wikimedia servers to propagage edit.
  (at-at/after 10000
    #(events/fire-input args)
    state/at-at-pool))

(def client (atom nil))

(defn new-client []
  (let [the-client (new RCStreamLegacyClient callback (-> config :source-config :wikimedia-dois :subscribe))]
    (.run the-client)))

(defn start []
  (info "Connect wikimedia...")
    ; The logger is mega-chatty (~50 messages per second at INFO). We have alternative ways of seeing what's going on.
    (info "Start wikimedia...")
    (.setLevel (Logger/getLogger "io.socket") Level/OFF)
    (info "Wikimedia running.")
    (try
      (reset! client (new-client))
      (catch Exception e (info "Error starting wikimedia client" (str e)))))

(defn restart []
  (info "Reconnect wikimedia...")
  (try
    (.stop @client)
    (catch Exception e (info "Error stoppping wikimedia " (str e))))
  (info "Stopped old wikimedia client...")
  (start)
  (info "Reconnected wikimedia"))