(ns tropology.routes.api
  (:require [liberator.core
             :refer [defresource resource request-method-in]]
            [clojure.string :refer [lower-case]]
            [compojure.core :refer [defroutes GET ANY]]
            [com.numergent.url-tools :as ut]
            [tropology.api :as api]
            [tropology.db :as db]
            [io.clojure.liberator-transit]))

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
             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])


(defresource network
             :allowed-methods [:get]
             :handle-ok (fn [request]
                          (let [{{{label :category, name :name} :params} :request} request
                                code (lower-case (str label "/" name))]
                            (api/network-from-node code)))
             ; Network does not return transit+jason because sigma wouldn't know what to do with it
             :available-media-types ["application/json"])

(defresource tropes
             :allowed-methods [:get]
             :exists? (fn [request]
                        (let [{{{label :category, name :name} :params} :request} request
                              code (lower-case (str label "/" name))]
                          (api/tropes-from-node code)))
             :handle-ok (fn [request]
                          (select-keys request [:title :description :references :code :display :image]))
             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])


(defroutes api-routes
           (ANY "/api/node/:category/:name" [category name] node)
           (ANY "/api/network/:category/:name" [category name] network)
           (ANY "/api/tropes/" [] tropes)
           (ANY "/api/tropes/:category/:name" [category name] tropes)
           (ANY "/api/home" request home))