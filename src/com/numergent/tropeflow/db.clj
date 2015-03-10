(ns com.numergent.tropeflow.db
  (:require [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest.labels :as nl]))


; TODO:
; - Get a connection
; - Create a trope node
; - Create a trope node for each link referenced, if it doesn't exist
; - Add all the references

(defn get-connection
  "Trivial. Returns a local connection."
  []
  (nr/connect "http://localhost:7474/db/data/"))


(defn create-node
  "Creates a node from a connection"
  [conn label data-items]
  (let [node (nn/create conn data-items)]
    (do
      (nl/add conn node label)
      node)))