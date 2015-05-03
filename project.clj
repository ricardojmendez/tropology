(defproject tropology "0.1.0-SNAPSHOT"
            :description "Tropology - Crawling and Visualizing TVTropes"
            :url "http://numergent.com/tags/tropology/"

            :dependencies [[org.clojure/clojure "1.6.0"]
                           [org.clojure/clojurescript "0.0-3123" :scope "provided"]
                           [cljs-ajax "0.3.10"]
                           [ring-server "0.4.0"]
                           [selmer "0.8.2"]
                           [com.taoensso/timbre "3.4.0"]
                           [com.taoensso/tower "3.0.2"]
                           [markdown-clj "0.9.65"]
                           [environ "1.0.0"]
                           [im.chit/cronj "1.4.3"]
                           [compojure "1.3.2"]
                           [ring/ring-defaults "0.1.4"]
                           [ring/ring-session-timeout "0.1.0"]
                           [ring-middleware-format "0.4.0"]
                           [noir-exception "0.2.3"]
                           [bouncer "0.3.2"]
                           [prone "0.8.1"]
                           [enlive "1.1.5"]
                           [com.curiosity/urly "2.0.0-alpha6"]
                           [clojure.joda-time "0.4.0"]
                           [http-kit "2.1.19"]
                           [reagent-forms "0.4.6"]
                           [reagent-utils "0.1.4"]
                           [secretary "1.2.2"]
                           [liberator "0.12.2"]
                           [cheshire "5.4.0"]
                           [korma "0.4.0"]
                           [org.postgresql/postgresql "9.4-1201-jdbc41"]
                           ]


            :cljsbuild {:builds
                        {:app
                         {:source-paths ["src-cljs"]
                          :compiler
                                        {:output-dir    "resources/public/js/out"
                                         :externs       ["react/externs/react.js" "resources/externs/sigma.js"]
                                         :optimizations :none
                                         :output-to     "resources/public/js/app.js"
                                         :source-map    "resources/public/js/out.js.map"
                                         :pretty-print  true
                                         }}}}

            :clean-targets ^{:protect false} ["resources/public/js"]

            :min-lein-version "2.0.0"
            :uberjar-name "tropology.jar"
            :repl-options {:init-ns tropology.handler}
            :jvm-opts ["-server"]

            :main tropology.core

            :plugins [[lein-ring "0.9.1"]
                      [lein-cljsbuild "1.0.4"]
                      [lein-environ "1.0.0"]
                      [lein-ancient "0.6.0"]
                      [clj-sql-up "0.3.7"]]


            :ring {:handler      tropology.handler/app
                   :init         tropology.handler/init
                   :destroy      tropology.handler/destroy
                   :uberwar-name "tropology.war"}

            :clj-sql-up {:database-test "jdbc:postgresql://192.168.59.103:5432/tropology_test?user=postgres&password=testdb"
                         :database      "jdbc:postgresql://192.168.59.103:5432/tropology?user=postgres&password=testdb"
                         :deps          [[org.postgresql/postgresql "9.4-1201-jdbc41"]]
                         }



            :profiles
            {
             :uberjar    {:omit-source true
                          :env         {:production  true
                                        :db-name     "tropology"
                                        :db-host     "localhost"
                                        :update-cron "0 /3 * * * * *"
                                        :update-size 3
                                        :expiration  14
                                        }
                          :hooks       [leiningen.cljsbuild]
                          :cljsbuild
                                       {:jar true
                                        :builds
                                             {:app
                                              {:source-paths ["env/prod/cljs"]
                                               :compiler     {:optimizations :advanced :pretty-print false}}}}

                          :aot         :all}
             :production {:ring {:open-browser? false
                                 :stacktraces?  false
                                 :auto-reload?  false}
                          :env  {:db-url      "http://neo4j:testneo4j@localhost:7474/db/data/"
                                 :update-cron "0 /3 * * * * *"
                                 :update-size 10
                                 :expiration  14}
                          :aot  :all
                          }
             :dev        {:dependencies [[ring-mock "0.1.5"]
                                         [ring/ring-devel "1.3.2"]
                                         [pjstadig/humane-test-output "0.7.0"]
                                         [leiningen "2.5.1"]
                                         [figwheel "0.2.5"]
                                         [weasel "0.6.0"]
                                         [com.cemerick/piggieback "0.1.6-SNAPSHOT"]]
                          :plugins      [[lein-figwheel "0.2.3-SNAPSHOT"]]

                          :figwheel
                                        {:http-server-root "public"
                                         :server-port      3449
                                         :css-dirs         ["resources/public/css"]
                                         :ring-handler     tropology.handler/app}
                          :repl-options {:init-ns tropology.repl}
                          :injections   [(require 'pjstadig.humane-test-output)
                                         (pjstadig.humane-test-output/activate!)]
                          :source-paths ["env/dev/clj"]
                          :cljsbuild    {:builds {:app {:source-paths ["env/dev/cljs"]}}}
                          :env          {:dev             true
                                         :db-name         "tropology"
                                         :db-host         "192.168.59.103"
                                         :db-user         "postgres"
                                         :db-password     "testdb"
                                         :update-cron     "0 /2 * * * * *"
                                         :update-size     5
                                         :update-disabled true
                                         :expiration      14
                                         }}
             :test       {:dependencies [[pjstadig/humane-test-output "0.7.0"]
                                         [leiningen "2.5.1"]]
                          :repl-options {:init-ns tropology.repl}
                          :injections   [(require 'pjstadig.humane-test-output)
                                         (pjstadig.humane-test-output/activate!)]
                          :source-paths ["env/dev/clj"]
                          :cljsbuild    {:builds {:app {:source-paths ["env/dev/cljs"]}}}
                          :env          {:dev             true
                                         :db-name         "tropology_test"
                                         :db-host         "192.168.59.103"
                                         :db-user         "postgres"
                                         :db-password     "testdb"
                                         :update-cron     "0 /2 * * * * *"
                                         :update-size     5
                                         :update-disabled true
                                         :expiration      10
                                         }}})
