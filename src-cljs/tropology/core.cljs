(ns tropology.core
  (:require [ajax.core :refer [GET POST PUT]]
            [reagent.core :as reagent :refer [atom]]
            [clojure.string :refer [lower-case]]
            [goog.object :as gobject]
            [secretary.core :as secretary]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields]]
            [ajax.core :refer [POST]])
  (:require-macros [secretary.core :refer [defroute]]))


(defn row [label & body]
  [:div.row
   [:div.col-md-2 [:span label]]
   [:div.col-md-3 body]])

(defn text-input [id label]
  (row label [:input.form-control {:field :text
                                   :id    id}]))


; Original code:

(defn nav-item [tag label url]
  [:li {:class (when (= tag (session/get :page)) "active")}
   [:a {:on-click #(secretary/dispatch! (str "#/" url))} label]])

(defn navbar []
  [:div.navbar.navbar-inverse.navbar-fixed-top
   [:div.container
    [:div.navbar-header
     [:a.navbar-brand {:href "#/"} "tropology"]]
    [:div.navbar-collapse.collapse
     [:ul.nav.navbar-nav
      (nav-item :home "Home" "")
      (nav-item :tropes "Tropes" "tropes")
      (nav-item :about "About" "about")
      ]]]])

(defn about-page []
  [:div
   [:main]
   [:div "this is the story of tropology... work in progress"]
   ])


; Graph!


(def state (atom {:sigma nil}))

(-> js/sigma .-classes .-graph (.addMethod "neighbors",
                                           (fn [node-id]
                                             (-> (js* "this")
                                                 .-allNeighborsIndex
                                                 (aget node-id)
                                                 gobject/getKeys)) ; The graph keeps the neighbors as properties
                                           ))


(defn in-seq? [s x]
  (some? (some #{x} s)))

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
    (swap! state assoc :sigma sig)
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
    (swap! state assoc :sigma sig)
    (.load db (str js/context "/api/network/" code) on-done)

    ))

(defn redraw-graph [trope-code]
  (let [current (:sigma @state)]                            ; Must be set by importer on creation
    (if current
      (do
        (.kill current)
        (swap! state assoc :sigma nil)))
    ; (create-graph (-> (.getElementById js/document "trope-code") .-value))
    (create-graph trope-code)
    ))




(defn list-tropes [trope-code]
  (.log js/console trope-code)
  (GET (str "/api/tropes/" (lower-case trope-code))
       {:handler (fn [response]
                   (.log js/console (str "Done obtaining " trope-code))
                   (.log js/console response)
                   (swap! state assoc :tropes response))}
       )
  )


(def trope-code-form
  [:div
   (text-input :trope-code "Article code:")])


(defn trope-data []
  (let [form-data (atom {})]
    (fn []
      [:div
       [bind-fields trope-code-form form-data]
       (cond
         (= :home (session/get :page)) [:div
                                        [:input {:type     "button"
                                                 :value    "Graph!"
                                                 :on-click #(redraw-graph (:trope-code @form-data))}]
                                        [:div {:id "graph-container"}]]
         (= :tropes (session/get :page)) [:div
                                          [:input {:type     "button"
                                                   :value    "List tropes"
                                                   :on-click #(list-tropes (:trope-code @form-data))}]
                                          [:div {:id "trope-list-container"}
                                           [:ul
                                            (for [trope (:tropes @state)]
                                              [:li {:dangerouslySetInnerHTML {:__html (trope "text")}}])
                                            ]
                                           ]]
         :else "Nope"
         )])
    ))


(def pages
  {:home   trope-data
   :tropes trope-data
   :about  about-page})


(defn page []
  [(pages (session/get :page))])

(defroute "/" [] (session/put! :page :home))
(defroute "/tropes" [] (session/put! :page :tropes))
(defroute "/about" [] (session/put! :page :about))


(defn init! []
  (secretary/set-config! :prefix "#")
  (session/put! :page :home)
  (reagent/render-component [navbar] (.getElementById js/document "navbar"))
  (reagent/render-component [page] (.getElementById js/document "app")))





