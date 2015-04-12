(ns tropology.core
  (:require [tropology.handler :refer [app init]]
            [org.httpkit.server :refer [run-server]])
  (:gen-class))

(defn -main [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (init)                                                  ; Init doesn't get called on uberjar
    (run-server app {:port port})))
