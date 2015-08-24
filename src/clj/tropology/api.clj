(ns tropology.api
  (:require [tropology.db :as db]
            [numergent.utils :as u]
            [taoensso.timbre.profiling :as prof]
            [tropology.base :as b]
            [clojure.string :as s]
            [tropology.parsing :as p]
            [net.cgrand.enlive-html :as e]))


;
; Edge and node functions
;


(defn node-size-multiplier [rel-count]
  (cond
    (nil? rel-count) 0.5
    (< rel-count 10) 1
    :else (* 2 (Math/log10 rel-count))
    ))


(defn rand-range [n]
  (- (rand n) (/ n 2)))

(defn color-from-code [code]
  (apply str (into ["#"] (take 6 (format "%x" (hash code)))))
  )


(defn transform-node
  "Transforms a node into the expected map values adds the coordinates"
  ([node]
   (let [raw  (hash (:code node))
         sign (Math/signum (float raw))
         x    (* sign (Math/log10 (Math/abs raw)))
         y    (* -1 sign (Math/log (Math/abs raw)))]
     (transform-node node x y)
     )
    )
  ([node x y]
   (-> node
       (select-keys [:code :url :title])
       (assoc :id (:code node))                             ; We could just send a hash as the id, which would be more succinct, but this allows for quicker debugging.
       (assoc :x x
              :y y
              :size (* (node-size-multiplier (:incoming node))
                       (count (:code node)))
              :color (color-from-code (:code node))
              :label (-> (:display node)
                         (s/split #"/")
                         second)))))

(defn edge
  "Returns an edge map. Does not return an index, since those are disposable
  and can be assigned later."
  [from to color]
  {:source from
   :target to
   :color  color
   :type   "arrow"})                                        ; Could be line, curve, arrow or curvedArrow

(defn edge-collection
  [{code       :code
    links-from :links-from
    links-to   :links-to
    color-from :color-from
    color-to   :color-to}]
  (->>
    (let [edges-from (map #(edge code %1 (or color-from (color-from-code code))) links-from)
          edges-to   (map #(edge %1 code (or color-to "#0066ff")) links-to)]
      (concat edges-from edges-to))
    (prof/p :edge-collection)))



(defn node-relationships
  [code-list]
  (->>
    (let [nodes       (->> (db/query-for-codes code-list)
                           (remove empty?)
                           (map transform-node))
          connections (db/query-rel-list code-list)
          groups      (->> (b/group-pairs connections)
                           (pmap #(hash-map :code (key %) :links-from (val %))))
          ]
      {:nodes nodes
       :edges (->> (pmap edge-collection groups)
                   flatten
                   (map-indexed #(assoc %2 :id %1)))}
      )
    (prof/profile :trace :node-relationships)
    ))


(defn network-from-node
  "Returns a network of nodes around a code, including: the node, all
  the nodes that either reference it or that it references, and the
  relationships between them.

  The node code is case sensitive."
  [code]
  (->>
    (let [node       (-> (db/query-by-code code) (transform-node 0 0))
          nodes-from (db/query-from code :LINKSTO)
          nodes-to   (db/query-to :LINKSTO code)
          node-set   (set (concat nodes-from nodes-to))
          related    (->> (db/query-common-nodes-from code :LINKSTO 1000)
                          (b/group-pairs)
                          (pmap #(hash-map :code (key %) :links-from (val %) :color-from "#00ffc7"))
                          )
          with-base  (conj related {:code code :links-from (pmap :code nodes-from) :links-to (pmap :code nodes-to)})]
      {:nodes (conj
                (pmap #(transform-node %1 (rand-range 50) (rand-range 50)) node-set)
                node)
       :edges (->>
                (pmap edge-collection with-base)
                flatten
                (map-indexed #(assoc %2 :id %1))
                )}
      )
    (prof/profile :trace :network-from-node)))


(defn tropes-from-node
  [code]
  (let [to-get (if (= code "/") (db/fetch-random-contents-code) code)
        node   (db/query-by-code to-get)
        html   (-> (if (:is-redirect node) (:redirects-to node) to-get)
                   db/get-html
                   (u/if-empty ""))
        res    (-> html java.io.StringReader. e/html-resource)
        tropes (p/get-tropes res)
        links  (map p/process-links tropes)
        node   (p/node-data-from-meta res)
        ]
    (if (empty? html)
      nil
      {:title       (:title node)
       :image       (:image node)
       :description (:description node)
       :code        (:code node)
       :display     (:display node)
       :url         (:url node)
       :references  (map #(hash-map :hiccup (:hiccup %)
                                    :links (map b/code-from-url (:links %)))
                         links)}))
  )