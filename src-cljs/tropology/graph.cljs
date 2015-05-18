(ns tropology.graph
  (:require [clojure.string :refer [lower-case]]
            [goog.object :as gobject]
            [reagent.session :as session]
            [tropology.utils :refer [in-seq?]]))




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



(defn refresh-graph [sig centerNodeId]
  (let [db    (aget sig "db")
        graph (-> sig .-graph)]
    (.killForceAtlas2 sig)
    (-> sig .-camera (.goTo (clj->js {:x 0 :y 0 :angle 0 :ratio 1})))
    (.clear graph)
    (.read graph (.neighborhood db centerNodeId))
    (let [nodes (.nodes graph)
          len   (.-length nodes)]
      (map-indexed (fn [i node]
                     (aset node "x" (Math/cos (/ (* 2 i Math/PI) len)))
                     (aset node "y" (Math/cos (/ (* 2 i Math/PI) len))))
                   nodes))
    (.refresh sig)
    (.startForceAtlas2 sig {:worker true :barnesHutOptimize false})
    (js/setTimeout #(.stopForceAtlas2 sig) 2000)
    (session/put! :sigma sig)
    )
  )

(defn create-graph [base-code]
  (let [code    (lower-case base-code)
        sig     (js/sigma. (clj->js {:renderer
                                               {:type      "canvas"
                                                :container (.getElementById js/document "graph-container")}
                                     :settings {:defaultNodeColor "#ec5148"
                                                :labelSizeRation  2
                                                :edgeLabelSize    "fixed"
                                                }}))
        db      (js/sigma.plugins.neighborhoods.)
        on-done (fn []
                  (do
                    (.bind sig "doubleClickNode", #((if-not (-> % .-data .-node .-center)
                                                      (refresh-graph sig (-> % .-data .-node .-id)))))
                    (refresh-graph sig code)
                    (.startForceAtlas2 sig {:worker true :barnesHutOptimize false})
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
                                 (do
                                   (aset node "color" (aget node "originalColor"))
                                   (aset node "showStatus"
                                         (if (= node-id code)
                                           "-"              ; Whatever, use default
                                           "a"              ; Always
                                           ))))
                               (doseq [node (groups false)]
                                 (do
                                   (aset node "color" "#eee")
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
    (aset sig "db" db)
    (session/put! :sigma sig)
    (.load db (str js/context "/api/network/" code) on-done)
    ))

(defn redraw-graph [trope-code]
  (let [current (session/get :sigma)]                            ; Must be set by importer on creation
    (if current
      (do
        (.kill current)
        (session/put! :sigma nil)))
    (create-graph trope-code)
    ))