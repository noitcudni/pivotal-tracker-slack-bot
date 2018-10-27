(defproject pivot-slack "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.json "0.2.6"]
                 [environ "1.1.0"]
                 [clj-http "3.9.1"]
                 [compojure "1.6.1"]
                 [http-kit "2.3.0"]
                 [ring-logger "1.0.1"]
                 [ring-transit "0.1.6"]
                 [ring/ring-defaults "0.3.2"]]
  :plugins [[lein-ring "0.12.4"]
            [lein-environ "1.1.0"]]
  :ring {:handler pivot-slack.handler/app
         :nrepl {:start? true}}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]]}})
