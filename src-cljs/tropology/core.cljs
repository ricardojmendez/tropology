(ns tropology.core
  (:require [ajax.core :refer [GET POST PUT]]
            [reagent.core :as reagent :refer [atom]]
            [clojure.string :refer [lower-case]]
            [reagent-forms.core :refer [bind-fields]]
            [re-frame.core :as re-frame]
            [tropology.graph :as graph]
            [tropology.utils :refer [in-seq?]]
            )
  (:require-macros [reagent.ratom :refer [reaction]]))

;
; Queries
;


(defn general-query
  [db [sid element-id]]
  (reaction (get-in @db [sid element-id])))

(re-frame/register-sub :ui-state general-query)
(re-frame/register-sub :article-data general-query)


;
; Handlers
;

(re-frame/register-handler
  :initialize
  (fn
    [db _]
    (merge db {:ui-state {:active-section :tropes}})))

(re-frame/register-handler
  :navbar-click
  (fn [app-state [_ item-key]]
    (assoc-in app-state [:ui-state :active-section] item-key)))


(re-frame/register-handler
  :load-article
  (fn [app-state [_ trope-code like-current?]]
    (if like-current?
      (re-frame/dispatch [:add-like (get-in app-state [:article-data :current-reference])]))
    (GET (str "/api/tropes/" (lower-case trope-code))
         {:handler #(re-frame/dispatch [:load-article-done %])})
    ; TODO Handle error
    app-state))


(re-frame/register-handler
  :load-article-done
  (fn [app-state [_ response]]
    (re-frame/dispatch [:pick-random-reference])
    (-> app-state
        (assoc-in [:article-data :current-article] response)
        (assoc-in [:article-data :tropes] (:tropes response)))))


(re-frame/register-handler
  :vote
  (fn [app-state [_ vote]]
    (let [current-ref (get-in app-state [:article-data :current-reference])]
      (if (= vote :like)
        (re-frame/dispatch [:add-like current-ref]))
      (re-frame/dispatch [:pick-random-reference])
      (assoc-in app-state
                [:article-data :tropes]
                (remove #(= % current-ref) (get-in app-state [:article-data :tropes])))
      )))


(re-frame/register-handler
  :add-like
  (fn [app-state [_ article-ref]]
    (let [like-list (get-in app-state [:article-data :like-list])]
      (if (not (in-seq? like-list article-ref))
        (assoc-in app-state [:article-data :like-list] (conj like-list article-ref))
        app-state)
      )))


(re-frame/register-handler
  :pick-random-reference
  (fn [app-state [_]]
    (let [tropes  (get-in app-state [:article-data :tropes])
          pick    (rand-nth tropes)
          element (if (nil? pick) {} pick)]
      (assoc-in app-state [:article-data :current-reference] element))))



;
; Element processing
;


(defn replace-link-for-dispatch
  "Given a hiccup structure that's assumed to be a link, we go through it
  to find the map of properties. If found, and one of these properties is
  a link with class 'twikilink', then we replace said link for a dispatch
  to a local route."
  [attrs extra-params]
  (if (and (map? attrs)
           (= "twikilink" (:class attrs))
           (:href attrs))
    (-> attrs
        (dissoc :href)
        (assoc :on-click #(re-frame/dispatch (into [] (concat [:load-article (:href attrs)] extra-params)))))
    attrs
    ))

(defn process-link [element extra-params]
  "Receives an element as a hiccup structure. If it's a link, then the link
  is replaced for an action dispatch, otherwise we return the same structure.
  "
  (if (and (vector? element)
           (= :a (first element)))
    (into [] (map #(replace-link-for-dispatch % extra-params) element))
    element))

(defn process-trope
  "Receives the hiccup data for a trope and processes it before display"
  [coll extra-params]
  (clojure.walk/prewalk #(process-link % extra-params) coll))


;
; Components
;

(defn row [label & body]
  [:div.row
   [:div.col-md-2 [:span label]]
   [:div.col-md-3 body]])

(defn text-input [id label]
  (row label [:input.form-control {:field :text
                                   :id    id}]))


(defn button-item [label dispatch-vals]
  [:li {:class "button"}
   [:a {:on-click #(re-frame/dispatch dispatch-vals)} label]])

(defn nav-item [selected item-key label]
  [:li {:class (when (= selected item-key) "active")}
   [:a {:on-click #(re-frame/dispatch [:navbar-click item-key])} label]])


(def trope-code-form
  [:div
   (text-input :trope-code "Article code:")])


(defn navbar []
  (let [selected (re-frame/subscribe [:ui-state :active-section])]
    (fn []
      (do
        [:div.navbar.navbar-inverse.navbar-fixed-top
         [:div.container
          [:div.navbar-header
           [:a.navbar-brand {:href "#/"} "tropology"]]
          [:div.navbar-collapse.collapse
           [:ul.nav.navbar-nav
            (nav-item @selected :home "Home")
            (nav-item @selected :tropes "Tropes")
            (nav-item @selected :about "About")
            ]]]]))
    ))



(defn article-display [form-data class]
  (let [current-article (re-frame/subscribe [:article-data :current-article])
        current-ref     (re-frame/subscribe [:article-data :current-reference])
        like-list       (re-frame/subscribe [:article-data :like-list])
        references      (re-frame/subscribe [:article-data :tropes])
        remaining       (reaction (count @references))
        ]
    [:div {:class class}
     [bind-fields trope-code-form form-data]
     [:div
      ; [:p "Hello article"]
      [:input {:type     "button"
               :value    "Retrieve references"
               :on-click #(re-frame/dispatch [:load-article (:trope-code @form-data) false])}]
      [:div {:id "current-trope"}
       [:h2 {:class "trope-title"} (:title @current-article)]
       [:p (:description @current-article)]]
      (if (some? @current-article)
        [:div {:id "current-piece"}
         [:h3 "Random reference"]
         [:p (process-trope @current-ref [true])]
         [:div [:span (str "(" @remaining " remaining)")]]
         [:div {:class "trope-vote"}
          [:ul
           [button-item "Interesting" [:vote :like]]
           [button-item "Skip" [:vote :skip]]]]]
        )
      (if (some? @like-list)
        [:div {:id "trope-list-container"}
         [:p "Selected items: "]
         [:ul
          (for [trope @like-list]
            ^{:key (hash trope)} [:li (process-trope trope [false])])]
         ])
      ]])
  )

(defn about-page [class]
  [:div {:class class}
   [:main]
   [:div "this is the story of tropology... work in progress"]
   ])


(defn app-display []
  (let [form-data (atom {:trope-code "Anime/SamuraiFlamenco"})
        ui-state  (re-frame/subscribe [:ui-state :active-section])
        ]
    (fn []
      [:div
       [:div {:class (when (not= @ui-state :home) "hidden")}
        [bind-fields trope-code-form form-data]
        [:div
         [:input {:type     "button"
                  :value    "Graph!"
                  :on-click #(graph/redraw-graph (:trope-code @form-data))}]
         [:div {:id "graph-container"}]]]
       [article-display form-data (when (not= @ui-state :tropes) "hidden")]
       [about-page (when (not= @ui-state :about) "hidden")]]
      )))


(defn init! []
  (re-frame/dispatch-sync [:initialize])
  (reagent/render [navbar] (.getElementById js/document "navbar"))
  (reagent/render [app-display] (.getElementById js/document "app")))
