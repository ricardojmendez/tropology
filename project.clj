(defproject tropefest "0.0.4-SNAPSHOT"
            :description "Tropefest! ... While we get a better name..."
            :url "http://numergent.com/"

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
                           [org.clojars.ricardojmendez/neocons "3.1.0-beta3-SNAPSHOT"]
                           [com.curiosity/urly "2.0.0-alpha6"]
                           [clojure.joda-time "0.4.0"]
                           [http-kit "2.1.19"]
                           [reagent-forms "0.4.6"]
                           [reagent-utils "0.1.4"]
                           [secretary "1.2.2"]
                           [liberator "0.12.2"]
                           [cheshire "5.4.0"]
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
            :uberjar-name "tropefest.jar"
            :repl-options {:init-ns tropefest.handler}
            :jvm-opts ["-server"]

            :main tropefest.core

            :plugins [[lein-ring "0.9.1"]
                      [lein-cljsbuild "1.0.4"]
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
                                         :ring-handler     tropefest.handler/app}
                          :repl-options {:init-ns tropefest.repl}
                          :injections   [(require 'pjstadig.humane-test-output)
                                         (pjstadig.humane-test-output/activate!)]
                          :source-paths ["env/dev/clj"]
                          :cljsbuild    {:builds {:app {:source-paths ["env/dev/cljs"]}}}
                          :env          {:dev             true
                                         :db-url          "http://neo4j:testneo4j@localhost:7474/db/data/"
                                         :update-cron     "0 /5 * * * * *"
                                         :update-size     2
                                         :update-disabled true
                                         :expiration      14
                                         }}})
