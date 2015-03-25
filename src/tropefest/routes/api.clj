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


(defresource network
             :allowed-methods [:get]
             :handle-ok (fn [request]
                          (let [{{{label :label, name :name} :params} :request} request
                                id (str label "/" name)]
                            {:nodes [{:id "n0" :label "A node" :x 0 :y 0 :size 3}
                                     {:id "n1" :label "Another node" :x 3 :y 1 :size 2}
                                     {:id "n2" :label "And a last node" :x 1 :y 3 :size 1}
                                     ]
                             :edges [{:id "e1" :source "n0" :target "n1"}
                                     {:id "e2" :source "n1" :target "n2"}
                                     {:id "e3" :source "n2" :target "n0"}]}))
             :available-media-types ["application/json"])



(defroutes api-routes
           (ANY "/api/node/:label/:name" [label name] node)
           (ANY "/api/network/:label/:name" [label name] network)
           (ANY "/api/home" request home))