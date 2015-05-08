(ns tropology.routes.api
  (:require [liberator.core
             :refer [defresource resource request-method-in]]
            [clojure.string :refer [lower-case]]
            [compojure.core :refer [defroutes GET ANY]]
            [com.numergent.url-tools :as ut]
            [tropology.api :as api]
            [tropology.db :as db]))

(defresource home
             :handle-ok "Hello World!"
             :etag "fixed-etag"
             :available-media-types ["text/plain"])

(defresource node
             :allowed-methods [:get]
             :handle-ok (fn [request]
                          (let [{{{category :category, name :name} :params} :request} request
                                id (str category "/" name)]
                            (->
                              (db/query-by-code id)
                              :data
                              (ut/if-empty {}))))
             :available-media-types ["application/json"])


(defresource network
             :allowed-methods [:get]
             :handle-ok (fn [request]
                          (let [{{{label :category, name :name} :params} :request} request
                                code (lower-case (str label "/" name))]
                            (api/network-from-node code)))
             :available-media-types ["application/json"])



(defroutes api-routes
           (ANY "/api/node/:category/:name" [category name] node)
           (ANY "/api/network/:category/:name" [category name] network)
           (ANY "/api/home" request home))