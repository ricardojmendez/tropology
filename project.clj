(defproject tropology "1.0.2-SNAPSHOT"
  :description "Tropology - Crawling and Visualizing TVTropes"
  :url "http://numergent.com/tags/tropology/"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [cljs-ajax "0.5.1"]
                 [ring-server "0.4.0"]
                 [com.taoensso/timbre "3.4.0"]
                 [selmer "0.9.4"]
                 [com.taoensso/tower "3.0.2"]
                 [markdown-clj "0.9.78"]
                 [environ "1.0.1"]
                 [im.chit/cronj "1.4.3"]
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-session-timeout "0.1.0"]
                 [ring-middleware-format "0.7.0"]
                 [noir-exception "0.2.5"]
                 [bouncer "0.3.3"]
                 [prone "0.8.2"]
                 [enlive "1.1.6"]
                 [com.curiosity/urly "2.0.0-alpha6"]
                 [clojure.joda-time "0.6.0"]
                 [http-kit "2.1.19"]
                 [reagent "0.5.1" :exclusions [cljsjs/react]]
                 [cljsjs/react-with-addons "0.14.0-1"]
                 [reagent-utils "0.1.5"]
                 [liberator "0.13"]
                 [cheshire "5.5.0"]
                 [korma "0.4.2"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [io.clojure/liberator-transit "0.3.0"]
                 [re-frame "0.5.0"]
                 [lein-doo "0.1.6-SNAPSHOT"]
                 ]

  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :test-paths ["test/clj" "test/cljs" "test/cljc"]


  :cljsbuild {:builds
              {:app
               {
                :source-paths ["src/cljs" "src/cljc"]
                :compiler     {:output-dir    "resources/public/js/"
                               :externs       ["react/externs/react.js" "resources/externs/sigma.js" "resources/externs/jquery-1.9.js"]
                               :optimizations :none
                               :output-to     "resources/public/js/core.js"
                               :source-map    "resources/public/js/core.js.map"
                               :pretty-print  true
                               :foreign-libs  [{:file     "resources/public/lib/sigma.min.js"
                                                :provides ["sigma"]}]
                               }
                }
               }
              }


  :clean-targets ^{:protect false} ["resources/public/js" "target"]

  :min-lein-version "2.0.0"
  :uberjar-name "tropology.jar"
  :repl-options {:init-ns tropology.handler}
  :jvm-opts ["-server"]

  :main tropology.core

  :doo {:build "test"}

  :plugins [[lein-ring "0.9.7"]
            [lein-cljsbuild "1.1.1"]
            [lein-environ "1.0.1"]
            [lein-ancient "0.6.8"]
            [clj-sql-up "0.3.7"]
            [lein-doo "0.1.6-SNAPSHOT"]
            ]


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
                :cljsbuild   {:jar true
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
                               [ring/ring-devel "1.4.0"]
                               [pjstadig/humane-test-output "0.7.0"]
                               [leiningen "2.5.3"]
                               [figwheel "0.4.1" :exclusions [org.clojure/clojure]]
                               ]
                :plugins      [[lein-figwheel "0.4.1"]]

                :figwheel     {:http-server-root "public"
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
                               [leiningen "2.5.3"]]
                :repl-options {:init-ns tropology.repl}
                :injections   [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]
                :source-paths ["test/clj"]
                :cljsbuild    {:builds {:app {:source-paths ["env/dev/cljs"]}
                                        :test
                                             {
                                              :source-paths ["src/cljs" "test/cljs" "test/cljc"]
                                              :compiler     {:output-to     "target/test/tropology-tests.js"
                                                             :output-dir    "target/test"
                                                             :optimizations :whitespace
                                                             :foreign-libs  [{:file     "resources/public/lib/sigma.min.js"
                                                                              :provides ["sigma"]}]
                                                             }}
                                        }}
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
