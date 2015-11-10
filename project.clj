(defproject wikipedia-watcher "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                  [camel-snake-kebab "0.3.2"]
                  [clj-http "1.0.1"]

                  [compojure "1.3.4"]
                  [http-kit "2.1.18"]
                  [korma "0.3.0"]
                  [korma "0.3.0"]
                  [liberator "0.12.2"]
                  [mysql-java "5.1.21"]
                  [mysql-java "5.1.21"]
                  [selmer "0.8.0"]
                 [clj-http "1.0.1"]
                 [clj-time "0.8.0"]
                 [crossref-util "0.1.7"]
                 [enlive "1.1.6"]
                 [gottox/socketio "0.1"]
                 [http-kit "2.1.18"]
                 [org.clojure/core.async "0.2.371"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [ring "1.3.2"]
                 [overtone/at-at "1.2.0"]]
                 
  :plugins [[lein-localrepo "0.5.3"]]
  :java-source-paths ["src-java"]
  :main ^:skip-aot wikipediawatcher.core
  :target-path "target/%s"
  :jvm-opts ["-Duser.timezone=UTC"]
  :profiles {:uberjar {:aot :all}})
