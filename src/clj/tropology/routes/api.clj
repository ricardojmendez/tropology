(ns tropology.routes.api
  (:require [liberator.core
             :refer [defresource resource request-method-in]]
            [clojure.string :refer [lower-case]]
            [compojure.core :refer [defroutes GET ANY]]
            [tropology.api :as api]
            [tropology.db :as db]
            [io.clojure.liberator-transit]
            [numergent.utils :as u]))


(def expiration-ms (* 1000 60 60 24))                       ; Graphs expire once a day
(defn default-last-modified []
  (* expiration-ms (long (/ (System/currentTimeMillis) expiration-ms))))

(defresource home
             :handle-ok "Hello World!"
             :etag "fixed-etag"
             :available-media-types ["text/plain"])


(defresource connections
             :allowed-methods [:get]
             :handle-ok (fn [ctx]
                          (let [query (get-in ctx [:request :params :code-list])]
                            (api/node-relationships query)
                            )
                          )
             ; Will be read by sigma, so don't return transit+json
             :available-media-types ["application/json"])



(defresource tropes
             :allowed-methods [:get]
             :exists? (fn [request]
                        (let [{{{label :category, name :name} :params} :request} request
                              code (lower-case (str label "/" name))]
                          (api/tropes-from-node code)))
             :handle-ok (fn [request]
                          (select-keys request [:title :description :references :code :display :image :url]))
             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])


(defroutes api-routes
           (ANY "/api/graph/connections/" request connections)
           (ANY "/api/tropes/" [] tropes)
           (ANY "/api/tropes/:category/:name" [category name] tropes)
           (ANY "/api/home" request home))