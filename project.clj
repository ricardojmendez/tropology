(defproject tropefest "0.0.1-SNAPSHOT"
            :description "FIXME: write description"
            :url "http://example.com/FIXME"

            :dependencies [[org.clojure/clojure "1.6.0"]
                           [org.clojure/clojurescript "0.0-3126"]
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
                           [clojurewerkz/neocons "3.1.0-beta2"]
                           [clojurewerkz/urly "2.0.0-alpha5"]
                           [clojure.joda-time "0.4.0"]
                           [http-kit "2.1.19"]
                           ]

            :cljsbuild {:builds
                        [{:source-paths ["src-cljs"],
                          :id           "dev",
                          :compiler
                                        {:output-dir    "resources/public/js/",
                                         :optimizations :none,
                                         :output-to     "resources/public/js/app.js",
                                         :source-map    true,
                                         :pretty-print  true}}
                         {:source-paths ["src-cljs"],
                          :id           "release",
                          :compiler
                                        {:closure-warnings {:non-standard-jsdoc :off},
                                         :optimizations    :advanced,
                                         :output-to        "resources/public/js/app.js",
                                         :output-wrapper   false,
                                         :pretty-print     false}}]}

            :min-lein-version "2.0.0"
            :uberjar-name "tropefest.jar"
            :repl-options {:init-ns tropefest.handler}
            :jvm-opts ["-server"]

            :main tropefest.core

            :plugins [[lein-ring "0.9.1"]
                      [lein-cljsbuild "1.0.5"]
                      [lein-environ "1.0.0"]
                      [lein-ancient "0.6.0"]]


            :ring {:handler      tropefest.handler/app
                   :init         tropefest.handler/init
                   :destroy      tropefest.handler/destroy
                   :uberwar-name "tropefest.war"}



            :profiles
            {
             :uberjar    {:omit-source true
                          :env         {:production  true
                                        :db-url      "http://neo4j:testneo4j@localhost:7474/db/data/"
                                        :update-cron "0 /3 * * * * *"
                                        :update-size 3
                                        }
                          :hooks       ['leiningen.cljsbuild]
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
                                 :update-size 10}
                          :aot  :all
                          }
             :dev        {:dependencies [[ring-mock "0.1.5"]
                                         [ring/ring-devel "1.3.2"]
                                         [pjstadig/humane-test-output "0.7.0"]
                                         [figwheel "0.2.5"]
                                         ]
                          :plugins      [[lein-figwheel "0.2.3-SNAPSHOT"]]

                          :cljsbuild    {:builds {:app {:source-paths ["env/dev/cljs"]}}}
                          :figwheel
                                        {:http-server-root "public"
                                         :server-port      3449
                                         :css-dirs         ["resources/public/css"]
                                         :ring-handler     todelete.handler/app}
                          :injections   [(require 'pjstadig.humane-test-output)
                                         (pjstadig.humane-test-output/activate!)]
                          :env          {:dev         true
                                         :db-url      "http://neo4j:testneo4j@localhost:7474/db/data/"
                                         :update-cron "0 /5 * * * * *"
                                         :update-size 2
                                         }}})
