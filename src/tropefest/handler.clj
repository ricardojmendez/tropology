(ns tropefest.handler
  (:require [compojure.core :refer [defroutes routes]]
            [tropefest.routes.home :refer [home-routes]]
            [tropefest.middleware
             :refer [development-middleware production-middleware]]
            [tropefest.db :as db]
            [tropefest.parsing :as p]
            [tropefest.session :as session]
            [ring.middleware.defaults :refer [site-defaults]]
            [compojure.route :as route]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.rotor :as rotor]
            [selmer.parser :as parser]
            [environ.core :refer [env]]
            [cronj.core :as cronj]
            [tropefest.db :as db]))

(defroutes base-routes
           (route/resources "/")
           (route/not-found "Not Found"))

(defn seed-database []
  (timbre/info "Seeding...")
  (if-not (db/query-by-id (db/get-connection) "Anime/SamuraiFlamenco")
    (->
      (p/load-resource-url "http://tvtropes.org/pmwiki/pmwiki.php/Anime/SamuraiFlamenco")
      p/save-page-links!)))

(defn update-handler [t opts]
  (timbre/info (str "Remaining " (count (db/query-nodes-to-crawl (db/get-connection) 9999999)) " updating " (:total opts) "... "))
  (try
    (p/crawl-and-update! (db/get-connection) (Integer. (:total opts)))
    (catch Throwable t (timbre/error (str "Exception while updating: " t)))
    )
  (timbre/info "Done"))

(def update-task
  {:id "update-task"
   :handler update-handler
   :schedule (:update-cron env)
   :opts {:total (:update-size env)}})

(def cj (cronj/cronj :entries [update-task]))

(defn init
  "init will be called once when
   app is deployed as a servlet on
   an app server such as Tomcat
   put any initialization code here"
  []

  (timbre/set-config!
    [:appenders :rotor]
    {:min-level             :info
     :enabled?              true
     :async?                false                           ; should be always false for rotor
     :max-message-per-msecs nil
     :fn                    rotor/appender-fn})

  (timbre/set-config!
    [:shared-appender-config :rotor]
    {:path "tropefest.log" :max-size (* 512 1024) :backlog 10})


  (timbre/info (str "Updating " (:update-size env) " using " (:update-cron env)))

  (seed-database)
  (cronj/start! cj)

  (if (env :dev) (parser/cache-off!))


  ;;start the expired session cleanup job
  (cronj/start! session/cleanup-job)
  (timbre/info "\n-=[ tropefest started successfully"
               (when (env :dev) "using the development profile") "]=-"))



(defn destroy
  "destroy will be called when your application
   shuts down, put any clean up code here"
  []
  (timbre/info "tropefest is shutting down...")
  (cronj/shutdown! session/cleanup-job)
  (timbre/info "shutdown complete!"))

(def app
  (-> (routes
        home-routes
        base-routes)
      development-middleware
      production-middleware))
