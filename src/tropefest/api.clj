(ns tropefest.api
  (:require [clojure.set :as set]
            [tropefest.db :as db]))



(defn transform-node
  "Transforms a node into the expected map values adds the coordinates"
  [node x y]
  (let [stringed (clojure.walk/stringify-keys node)]
    (-> stringed
        (select-keys ["id" "url" "title"])
        (assoc "x" x "y" y "size" 3 "label" (stringed "id")))))

(defn edge
  "Returns an edge map"
  [i from to]
  {:id     (str "e" i)
   :source from
   :target to
   :type "arrow"})                                    ; Could be line, curve, arrow or curvedArrow

(defn edge-collection
  [node links-from links-to]
  (let [c (count links-from)
        edges-from (map-indexed #(edge %1 (:id node) (%2 "id")) links-from)
        edges-to (map-indexed #(edge (+ %1 c) (%2 "id") (:id node)) links-to)]
    (concat edges-from edges-to)))

(defn node-range [n]
  (- (rand n) (/ n 2)))

(defn network-from-node
  [code]
  (let [conn (db/get-connection)
        node (:data (db/query-by-id conn code))
        links-from (db/query-from conn code :LINKSTO)
        links-to (db/query-to conn :LINKSTO code)
        links-set (set (concat links-from links-to))]
    {:nodes (concat
              [(transform-node node 0 0)]
              (map #(transform-node %1 (node-range 50) (node-range 50)) links-set)
              ; (map-indexed #(transform-node %2 %1 1) links-from)
              ; (map-indexed #(transform-node %2 %1 2) links-to)
              )
     :edges (edge-collection node links-from links-to)})
  )