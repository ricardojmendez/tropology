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
    (re-frame/dispatch [:load-article "anime/samuraiflamenco" false])
    (merge db {:ui-state {:active-section :tropes}})))

(re-frame/register-handler
  :navbar-click
  (fn [app-state [_ item-key]]
    (assoc-in app-state [:ui-state :active-section] item-key)))


(re-frame/register-handler
  :load-article
  (fn [app-state [_ trope-code like-current?]]
    (if like-current?
      (re-frame/dispatch [:add-like
                          (get-in app-state [:article-data :current-reference])
                          (get-in app-state [:article-data :current-article])]))
    (GET (str "/api/tropes/" (lower-case trope-code))
         {:handler       #(re-frame/dispatch [:load-article-done %])
          :error-handler #(re-frame/dispatch [:load-article-error %])})
    app-state))


(re-frame/register-handler
  :load-article-done
  (fn [app-state [_ response]]
    (re-frame/dispatch [:clear-errors])
    (re-frame/dispatch [:pick-random-reference])
    (-> app-state
        (assoc-in [:article-data :current-article] response)
        (assoc-in [:article-data :tropes] (:tropes response)))))


(re-frame/register-handler
  :vote
  (fn [app-state [_ vote]]
    (let [current-ref (get-in app-state [:article-data :current-reference])]
      (if (= vote :like)
        (re-frame/dispatch [:add-like current-ref (get-in app-state [:article-data :current-article])]))
      (re-frame/dispatch [:pick-random-reference])
      (assoc-in app-state
                [:article-data :tropes]
                (remove #(= % current-ref) (get-in app-state [:article-data :tropes])))
      )))


(re-frame/register-handler
  :add-like
  (fn [app-state [_ article-ref current-article]]
    (let [like-list (get-in app-state [:article-data :like-list])
          element   {:ref article-ref :code (:code current-article) :display (:display current-article) :image (:image current-article)}
          ]
      #_ (.log js/console current-article)
      (if (not (in-seq? like-list element))
        (assoc-in app-state [:article-data :like-list] (conj like-list element))
        app-state)
      )))

(re-frame/register-handler
  :remove-like
  (fn [app-state [_ article-ref]]
    (.log js/console (str "Removing " article-ref))
    (assoc-in app-state [:article-data :like-list] (remove #(= article-ref (:ref %)) (get-in app-state [:article-data :like-list])))
    ))


(re-frame/register-handler
  :pick-random-reference
  (fn [app-state [_]]
    (let [tropes  (get-in app-state [:article-data :tropes])
          pick    (rand-nth (not-empty tropes))
          element (if (nil? pick) {} pick)]
      (assoc-in app-state [:article-data :current-reference] element))))

(re-frame/register-handler
  :load-article-error
  (fn [app-state [_ error]]
    (.log js/console (str "Error loading article: " (:status-text error)))
    (assoc-in app-state [:ui-state :errors] (cons (:status-text error) (get-in app-state [:ui-state :errors])))
    ))

(re-frame/register-handler
  :clear-errors
  (fn [app-state [_]]
    (assoc-in app-state [:ui-state :errors] nil)))

(re-frame/register-handler
  :draw-graph
  (fn [app-state [_ code]]
    (graph/redraw-graph code)
    app-state))



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

(defn add-key-meta
  "Add a meta with the key to all elements in the vector. It's usually called
   when we are processing a series of :li vectors, since React expects them to
   have a unique key."
  [elements]
  (loop [remaining elements
         acc       []]
    (if (empty? remaining)
      acc
      (recur (rest remaining)
             (conj acc ^{:key (hash (first remaining))} (first remaining))))))

(defn process-element [element extra-params]
  "Receives an element as a hiccup structure. If it's a link, then the link
   is replaced for an action dispatch; for a :ul, it adds a key as meta to all
   the nested elements; otherwise we return the same structure."
  (if (vector? element)
    (condp = (first element)
      :a (into [] (map #(replace-link-for-dispatch % extra-params) element))
      :ul (into [:ul] (add-key-meta (rest element)))
      element)
    element
    ))

(defn process-trope
  "Receives the hiccup data for a trope and processes it before display"
  [coll extra-params]
  (clojure.walk/prewalk #(process-element % extra-params) coll))


;
; Components
;

(defn button-item [label class dispatch-vals is-disabled? extra-items]
  [:button {:type :button :class (str "btn " class) :disabled is-disabled? :on-click #(re-frame/dispatch dispatch-vals)} extra-items label])


(defn header-display []
  (let [current-article (re-frame/subscribe [:article-data :current-article])]
    (fn []
      [:span
       [:img {:class "profile-image img-responsive pull-left" :src (:image @current-article) :alt (:title @current-article)}]
       [:div {:class "profile-content"}
        [:h1 {:class "name"} (:title @current-article)]
        [:p (:description @current-article)]]
       [button-item "Random Article" "btn-cta-primary pull-right" [:load-article ""] false [:i {:class "fa fa-paper-plane"}]]
       ]
      )))

(defn reference-display []
  (let [current-ref (re-frame/subscribe [:article-data :current-reference])
        references  (re-frame/subscribe [:article-data :tropes])
        remaining   (reaction (count @references))]
    (fn []
      [:span
       [:div {:class "desc text-left"}
        [:p (process-trope @current-ref [true])]]
       [button-item "Interesting" "btn-info btn-cta-secondary" [:vote :like] (empty? @current-ref) [:i {:class "fa fa-thumbs-o-up"}]]
       [button-item "Skip" "btn-default" [:vote :skip] (>= 0 @remaining)]
       [:p {:class "summary"} (str "(" @remaining " remaining)")]
       ])
    ))


(defn trope-row [{:keys [ref code display image]}]
  [:div {:class "item row"}
   [:a {:class "col-md-2 col-sm-2 col-xs-12" :on-click #(re-frame/dispatch [:load-article code false])}
    [:img {:class "img-responsive project-image" :src image}]]
   [:div {:class "desc col-md-10 col-sm-10 col-xs-12"}
    [:p (process-trope ref [false])]
    [:p
     [:a {:class "more-link" :on-click #(re-frame/dispatch [:load-article code false])}
      [:li {:class "fa fa-external-link"}]
      display]]
    ]
   [button-item "Remove" "btn-danger pull-right to-bottom" [:remove-like ref] false [:i {:class "fa fa-remove"}]]
   ]
  )

(defn like-list-display []
  (let [like-list (re-frame/subscribe [:article-data :like-list])]
    (fn []
      [:div
       (for [trope @like-list]
         ^{:key (hash trope)} [trope-row trope])
       ])))

(defn error-list-display []
  (let [errors (re-frame/subscribe [:ui-state :errors])]
    (fn []
      (if @errors
        [:section {:class "section latest has-error form-group" :on-click #(re-frame/dispatch [:clear-errors])}
         [:div {:class "section-inner"}
          [:h2 {:class "heading"} "There seems to have been a problem..."]
          [:div {:class "content"}
           (for [error @errors]
             ^{:key (rand-int 999999)} [:div [:label {:class "control-label"} error]])
           ]
          ]]
        ))))


(defn init! []
  (re-frame/dispatch-sync [:initialize])
  (reagent/render [header-display] (.getElementById js/document "header"))
  (reagent/render [reference-display] (.getElementById js/document "current-reference"))
  (reagent/render [like-list-display] (.getElementById js/document "like-list"))
  (reagent/render [error-list-display] (.getElementById js/document "error-list"))
  )
