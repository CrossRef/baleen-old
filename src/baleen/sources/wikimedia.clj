(ns baleen.sources.wikimedia
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
            [clojure.set :refer [difference]]))

(defn fetch-page-dois
  "Fetch the set of DOIs that are mentioned in the given URL."
  [url title revision]
  ; If we've fetched this before it's ready, we get an empty page with this link.
  ; It's differenet for each language, so the only common thing is '/delete'.
  ; Keep trying until we get something.
  (try-try-again {:sleep 5000 :tries 10 :return? :truthy?}
      (fn []
        (let [result (http/get url {:query-params {:title title :oldid revision}})
              body (or (:body @result) "")
              links (util/extract-links-from-html body)
              has-deleted-link (some (fn [link]
                                            (when-let [href (-> link :attrs :href)] (.contains href "/delete")))  links)
              quotes-revision (.contains body (str revision))
              dois (util/filter-doi-links links)
              fail (and (empty? dois) has-deleted-link quotes-revision)]

          (when fail (info "Failed" title revision "retry" ))
          (when-not fail dois)))))

(defn doi-changes [server-name title old-revision new-revision]
  (let [fetch-url (str "https://" server-name "/w/index.php")
        page-url (str "https://" server-name "/w/index.php?title=" (URLEncoder/encode title "UTF-8"))
        old-dois (fetch-page-dois fetch-url title old-revision)
        new-dois (fetch-page-dois fetch-url title new-revision)
        

        added-dois  (difference new-dois old-dois)
        removed-dois (difference old-dois new-dois)]
  [added-dois removed-dois]))

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
      (let [[added-dois removed-dois] (doi-changes server-name title old-revision new-revision)
            fetch-url (str "https://" server-name "/w/index.php")
              ; this method is private but this is better than copy-pasting.
              url (str fetch-url "?" (#'http/query-string {:title title}))]
            (when (or (not-empty added-dois) (not-empty removed-dois))
              (reset! state/most-recent-citation (clj-time/now)))

            ; Broadcast this to all listeners.
            (doseq [doi added-dois]
              (let [event-key (json/write-str [old-revision new-revision doi title server-name "cite"])]
                (events/fire-citation event-key doi date url "cite")))

            (doseq [doi removed-dois]
              (let [event-key (json/write-str [old-revision new-revision doi title server-name "uncite"])]
                (events/fire-citation event-key doi date url "uncite")))))))

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


(defn export [event-key doi date url action]
  (let [[old-revision new-revision doi title server action] (json/read-str event-key)
        pretty-url (str server "/wiki/" title)
        action-url (str "https://" server "/w/index.php?" (#'http/query-string {:title title :type "revision" :oldid old-revision :diff new-revision}))]
  {:input-container-title (server-name server)
   :date date
   :doi doi
   :title title
   :url url
   :pretty-url pretty-url
   :action-url action-url
   :action action}))

(defn- callback [type-name args]
  (events/fire-input args))

(def client (atom nil))

(defn start []
  (info "Connect wikimedia...")
  (let [the-client (new RCStreamLegacyClient callback (-> config :source-config :wikimedia :subscribe))
        logger (Logger/getLogger "io.socket")]
    ; The logger is mega-chatty (~50 messages per second at INFO). We have alternative ways of seeing what's going on.
    (.setLevel logger Level/OFF)
    (reset! client the-client)
    (info "Start wikimedia...")
    (.run the-client)
    (info "Wikimedia running.")))

(defn restart []
  (info "Reconnect wikimedia...")
  (.reconnect @client)  
  (info "Reconnected wikimedia"))