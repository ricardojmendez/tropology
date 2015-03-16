(ns tropefest.core
  (:require [tropefest.handler :refer [app init]]
    [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(defn -main [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (init)                                                  ; Init doesn't get called on uberjar
    (run-jetty app {:port port :join? false})))
