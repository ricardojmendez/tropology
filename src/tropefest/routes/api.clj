(ns tropefest.routes.api
  (:require [liberator.core
             :refer [defresource resource request-method-in]]
            [compojure.core :refer [defroutes GET ANY]]
            [com.numergent.url-tools :as ut]
            [tropefest.db :as db]))

(defresource home
             :handle-ok "Hello World!"
             :etag "fixed-etag"
             :available-media-types ["text/plain"])

(defresource node
             :allowed-methods [:get]
             :handle-ok (fn [request]
                          (let [{{{label :label, name :name} :params} :request} request
                                id (str label "/" name)]
                            (->
                              (db/query-by-id (db/get-connection) id)
                              :data
                              (ut/if-empty {}))))
             :available-media-types ["application/json"])


(defroutes api-routes
           (ANY "/api/node/:label/:name" [label name] node)
           (ANY "/api/home" request home))