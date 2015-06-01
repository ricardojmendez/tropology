(ns tropology.dev
  (:require [leiningen.core.main :as lein]))


(defn start-figwheel []
  (future
    (print "Starting figwheel.\n")
    (lein/-main ["figwheel"])))
