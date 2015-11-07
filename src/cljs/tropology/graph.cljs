(ns tropology.graph
  (:require [clojure.string :refer [lower-case]]
            [goog.object :as gobject]
            [reagent.session :as session]
            [re-frame.core :as re-frame]
            [numergent.utils :refer [in-seq?]]
            [sigma]))




;
; Graph!
;


; Add a function for finding the neighbors to a node
(let [graph (-> js/sigma .-classes .-graph)]
  (if (not (.hasMethod graph "neighbors"))
    (.addMethod graph "neighbors",
                (fn [node-id]
                  (-> (js* "this")
                      .-allNeighborsIndex
                      (aget node-id)
                      gobject/getKeys))                     ; The graph keeps the neighbors as properties
                )))


(defn create-graph [code-list]
  (let [sig     (js/sigma. (clj->js {:renderer
                                               {:type      "canvas"
                                                :container (.getElementById js/document "graph-area")}
                                     :settings {:defaultNodeColor "#ec5148"
                                                :labelSizeRation  2
                                                :edgeLabelSize    "fixed"
                                                }}))
        request (->> (map js/encodeURIComponent code-list)
                     (map #(str "code-list=" %))
                     (interpose "&")
                     (apply str))
        on-done (fn []
                  (do
                    (.bind sig "doubleClickNode", #(if-not (-> % .-data .-node .-center)
                                                    (re-frame/dispatch [:load-article (-> % .-data .-node .-id)])))

                    (.startForceAtlas2 sig
                                       (clj->js {:worker                         true
                                                 :barnesHutOptimize              true
                                                 :adjustSizes                    true
                                                 :scalingRatio                   10
                                                 :outboundAttractionDistribution true}))
                    (js/setTimeout #(.stopForceAtlas2 sig) 5000)
                    ; Set the colors
                    (gobject/forEach (-> sig .-graph .nodes)
                                     #(aset % "originalColor" (aget % "color")))
                    (gobject/forEach (-> sig .-graph .edges)
                                     #(aset % "originalColor" (aget % "color")))

                    (.bind sig "clickNode"
                           (fn [clicked]
                             (let [nodes         (-> sig .-graph .nodes) ; Re-bind in case it changed
                                   edges         (-> sig .-graph .edges)
                                   node-id       (-> clicked .-data .-node .-id)
                                   nodes-to-keep (-> (.neighbors (.-graph sig) node-id) (.concat node-id))
                                   groups        (group-by #(in-seq? nodes-to-keep (.-id %)) nodes)]
                               (doseq [node (groups true)]
                                 (aset node "color" (aget node "originalColor"))
                                 (aset node "showStatus" "t"))
                               (doseq [node (groups false)]
                                 (do
                                   (aset node "color" "#ddd")
                                   (aset node "showStatus" "n"))) ; Never
                               (.forEach edges              ; One idiomatic, one not as much
                                         (fn [edge]
                                           (if (and
                                                 (in-seq? nodes-to-keep (.-source edge))
                                                 (in-seq? nodes-to-keep (.-target edge)))
                                             (aset edge "color" (aget edge "originalColor"))
                                             (aset edge "color" "#eee"))))
                               (.refresh sig)))
                           )
                    ))
        ]
    (session/put! :sigma sig)
    (js/sigma.parsers.json (str js/context "/api/graph/connections/?" request) sig on-done)
    ))

(defn redraw-graph [code-list]
  (let [current (session/get :sigma)]                       ; Must be set by importer on creation
    (if current
      (do
        (.kill current)
        (session/put! :sigma nil)))
    (create-graph code-list)
    ))