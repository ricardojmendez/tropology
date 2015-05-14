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

(defn button-item [tag label url]
  [:li {:class "button"}
   [:a {:on-click #(secretary/dispatch! (str "#/" url))} label]])

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


(def state (atom {:sigma         nil
                  :current-trope nil
                  :tropes        nil
                  :like-list     []
                  :current-piece {}}))


; Add a function for finding the neighbors to a node
(let [graph (-> js/sigma .-classes .-graph)]
  (if (not (.hasMethod graph "neighbors"))
    (.addMethod graph "neighbors",
                (fn [node-id]
                  (-> (js* "this")
                      .-allNeighborsIndex
                      (aget node-id)
                      gobject/getKeys))                     ; The graph keeps the neighbors as properties
                )
    )
  )


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



;
; Element processing
;


(defn replace-link-for-dispatch
  "Given a hiccup structure that's assumed to be a link, we go through it
  to find the map of properties. If found, and one of these properties is
  a link with class 'twikilink', then we replace said link for a dispatch
  to a local route."
  [attrs]
  (if (and (map? attrs)
           (= "twikilink" (:class attrs))
           (:href attrs))
    (-> attrs
        (dissoc :href)
        (assoc :on-click #(secretary/dispatch! (:href attrs)))
        )
    attrs
    )
  )

(defn process-link [element]
  "Receives an element as a hiccup structure. If it's a link, then the link
  is replaced for an action dispatch, otherwise we return the same structure.
  "
  (if (and (vector? element)
           (= :a (first element)))
    (into [] (map replace-link-for-dispatch element))
    element))

(defn process-trope
  "Receives the hiccup data for a trope and processes it before display"
  [coll]
  (clojure.walk/prewalk process-link coll))


;
; Trope list
;


(defn pick-random-piece []
  (let [pick    (rand-nth (:tropes @state))
        element (if (nil? pick) {} pick)]
    (swap! state assoc :current-piece element))
  )


(defn add-current-to-like-list []
  (let [like-list (:like-list @state)
        current   (:current-piece @state)]
    (.log js/console (str "Current: " current))
    (if (not (in-seq? like-list current))
      (swap! state assoc :like-list (conj like-list current))) ; Can be unified with the reference on vote-on-piece
    )
  )

(defn vote-on-piece [vote]
  (if (= vote :like)
    (add-current-to-like-list))
  (swap! state assoc :tropes (remove #(= % (:current-piece @state)) (:tropes @state)))
  (pick-random-piece)
  )

(defn list-tropes [trope-code]
  (.log js/console (str "Loading " trope-code))
  (GET (str "/api/tropes/" (lower-case trope-code))
       {:handler (fn [response]
                   (.log js/console (str "Done obtaining " trope-code))
                   (swap! state assoc :current-trope response :tropes (:tropes response))
                   (pick-random-piece)
                   )}
       )
  )

(defn handle-trope-view
  "Called when a trope view is dispatched for a code"
  [code]
  (add-current-to-like-list)
  (list-tropes code)
  )


(def trope-code-form
  [:div
   (text-input :trope-code "Article code:")])


(defn trope-data []
  (let [form-data (atom {:trope-code "Anime/SamuraiFlamenco"})]
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
                                                   :value    "Retrieve tropes"
                                                   :on-click #(list-tropes (:trope-code @form-data))}]
                                          [:div {:id "current-trope"}
                                           [:h2 {:class "trope-title"} (get-in @state [:current-trope :title])]
                                           [:p (get-in @state [:current-trope :description])]
                                           ]
                                          [:div {:id "current-piece"}
                                           [:h3 "Random reference"]
                                           [:p (process-trope (:current-piece @state))]
                                           (if (:current-piece @state)
                                             [:div [:span (str "(" (count (:tropes @state)) " remaining)")]]

                                             [:div {:class "trope-vote"}
                                              [:ul
                                               (button-item :trope-like "Interesting" "vote/like")
                                               (button-item :trope-like "Skip" "vote/skip")
                                               ]
                                              ]
                                             )
                                           [:div {:id "trope-list-container"}
                                            [:p "Selected items: "]
                                            [:ul
                                             (for [trope (:like-list @state)]
                                               [:li (process-trope trope)])
                                             ]
                                            ]
                                           ]
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
(defroute "/vote/like" [] (vote-on-piece :like))
(defroute "/vote/skip" [] (vote-on-piece :skip))
(defroute "/view/:label/:name" [label name] (handle-trope-view (str label "/" name)))
; TODO: Add the current trope to the list of liked tropes only if the user clicks on a
; view link from the main trope display.  Currently it gets added anyway.

(defn init! []
  (secretary/set-config! :prefix "#")
  (session/put! :page :tropes)
  (reagent/render-component [navbar] (.getElementById js/document "navbar"))
  (reagent/render-component [page] (.getElementById js/document "app")))
