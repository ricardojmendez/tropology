(ns tropology.handler
  (:require [compojure.core :refer [defroutes routes]]
            [tropology.routes.api :refer [api-routes]]
            [tropology.routes.home :refer [home-routes]]
            [tropology.middleware
             :refer [development-middleware production-middleware]]
            [tropology.db :as db]
            [tropology.parsing :as p]
            [tropology.session :as session]
            [ring.middleware.defaults :refer [site-defaults]]
            [compojure.route :as route]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.rotor :as rotor]
            [selmer.parser :as parser]
            [environ.core :refer [env]]
            [cronj.core :as cronj]
            [tropology.db :as db]))

(defroutes base-routes
           (route/resources "/")
           (route/not-found "Not Found"))




;
; Tasks
;

(def state (atom {:isRunning false}))

(defn crawl-handler [t opts]
  (if (false? (:isRunning @state))
    (do
      (swap! state assoc :isRunning true)
      (try
        (do
          (timbre/info (str "Remaining " (count (db/query-nodes-to-crawl (db/get-connection) 9999999)) " updating " (:total opts) "... "))
          (p/crawl-and-update! (db/get-connection) (Integer. (:total opts))))
        (catch Throwable t (timbre/error (str "Exception while crawling: " t))))
      (timbre/info "Done crawling.")
      (swap! state assoc :isRunning false))
    (timbre/info "Not crawling because of on-going import process.")))


(defn update-handler [t opts]
  (timbre/info "Updating link totals")
  (try
    (db/update-link-count! (db/get-connection))
    (catch Throwable t (timbre/error (str "Exception while updating: " t))))
  (timbre/info "Done updating."))



(def crawl-task
  {:id       "crawl-task"
   :handler  crawl-handler
   :schedule (:update-cron env)
   :opts     {:total (:update-size env)}})

(def update-task
  {:id       "update-task"
   :handler  update-handler
   :schedule "0 0 /6 * * * *"
   :opts     {}})

(def cj (cronj/cronj :entries [crawl-task update-task]))


;
; Functions
;

(defn seed-database []
  (do
    (timbre/info "Seeding...")
    (if-not (db/query-by-code (db/get-connection) "Anime/SamuraiFlamenco")
      (->
        (p/load-resource-url "http://tvtropes.org/pmwiki/pmwiki.php/Anime/SamuraiFlamenco")
        p/save-page-links!))
    ; Uncomment to update the link count. Not necessary unless for some
    ; reason you have an incomplete database.
    ; (update-handler nil nil)
    (timbre/info "Done seeding.")))


(defn init
  "init will be called once when
   app is deployed as a servlet on
   an app server such as Tomcat
   put any initialization code here"
  []

  (timbre/info "Initializing...")

  (timbre/set-config!
    [:appenders :rotor]
    {:min-level             :info
     :enabled?              true
     :async?                false                           ; should be always false for rotor
     :max-message-per-msecs nil
     :fn                    rotor/appender-fn})

  (timbre/set-config!
    [:shared-appender-config :rotor]
    {:path "tropology.log" :max-size (* 512 1024) :backlog 10})


  (timbre/info (str "Updating " (:update-size env) " using " (:update-cron env)))


  ; TODO: Make crawling a separate process we can start
  (if (:update-disabled env)
    (timbre/warn "AUTO-UPDATES ARE DISABLED")
    (do
      (seed-database)
      (cronj/start! cj)))


  (if (env :dev) (parser/cache-off!))

  ;;start the expired session cleanup job
  (cronj/start! session/cleanup-job)
  (timbre/info "\n-=[ tropology started successfully"
               (when (env :dev) "using the development profile") "]=-"))



(defn destroy
  "destroy will be called when your application
   shuts down, put any clean up code here"
  []
  (timbre/info "tropology is shutting down...")
  (cronj/shutdown! session/cleanup-job)
  (cronj/shutdown! cj)
  (timbre/info "shutdown complete!"))

(def app
  (-> (routes
        api-routes
        home-routes
        base-routes)
      development-middleware
      production-middleware))
