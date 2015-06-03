(ns tropology.core
  (:require [ajax.core :refer [GET POST PUT]]
            [reagent.core :as reagent :refer [atom]]
            [clojure.string :refer [lower-case split trim]]
            [clojure.set :refer [union]]
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
    (merge db {:ui-state {:show-graph?    false
                          :active-section :references}})))

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
    ; TODO: On load done, move the page up to the top
    (re-frame/dispatch [:clear-errors])
    (re-frame/dispatch [:pick-random-reference])
    (-> app-state
        (assoc-in [:article-data :current-article] response)
        (assoc-in [:article-data :references] (:references response)))))


(re-frame/register-handler
  :vote
  (fn [app-state [_ vote]]
    (let [current-ref (get-in app-state [:article-data :current-reference])]
      (if (= vote :like)
        (re-frame/dispatch [:add-like current-ref (get-in app-state [:article-data :current-article])]))
      (re-frame/dispatch [:pick-random-reference])
      (assoc-in app-state
                [:article-data :references]
                (remove #(= % current-ref) (get-in app-state [:article-data :references])))
      )))


(re-frame/register-handler
  :add-like
  (fn [app-state [_ article-ref current-article]]
    (let [like-list (get-in app-state [:article-data :like-list])
          element   {:ref article-ref :code (:code current-article) :display (:display current-article) :image (:image current-article)}
          ]
      ; (.log js/console current-article)
      ; (.log js/console article-ref)
      (if (not (in-seq? like-list element))
        (assoc-in app-state [:article-data :like-list] (conj like-list element))
        app-state)
      )))

(re-frame/register-handler
  :remove-like
  (fn [app-state [_ article-ref]]
    #_ (.log js/console (str "Removing " article-ref))
    (assoc-in app-state [:article-data :like-list] (remove #(= article-ref (:ref %)) (get-in app-state [:article-data :like-list])))
    ))


(re-frame/register-handler
  :pick-random-reference
  (fn [app-state [_]]
    (let [tropes  (get-in app-state [:article-data :references])
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
  :set-show-graph
  (fn [app-state [_ show?]]
    (assoc-in app-state [:ui-state :show-graph?] show?)
    ))

(re-frame/register-handler
  :draw-graph
  (fn [app-state [_ code-list]]
    (graph/redraw-graph code-list)
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
   have a unique key. Does not consider the possibility that the link might
   be external - if that's the case, the even handler will just report that it
   was unable to load the article."
  [elements]
  (loop [remaining elements
         acc       []]
    (if (empty? remaining)
      acc
      (recur (rest remaining)
             (conj acc ^{:key (hash (first remaining))} (first remaining))))))


(defn process-a
  "For an :a element, replace the link with a dispatch command so that we can
  load the relevant article."
  [element extra-params]
  (if (= :a (first element))
    (into [] (map #(replace-link-for-dispatch % extra-params) element))
    element))

(defn process-ul
  "For every li inside a ul element that we are about to render, make sure that
  we add a key as part of the metadata to avoid raising issues with React."
  [element]
  (if (= :ul (first element))
    (into [:ul] (add-key-meta (rest element)))
    element))

(defn process-span
  "Handle two cases which could cause trouble with span elements.

  If the element received is a span, and the class for it indicates that it's
  a note label, discard the element altogether.  These notes are a dispaly
  peculiarity from TVTropes that we don't particularly case about.

  Also remove any pre-existing onclick attributes, as we don't want to
  keep any code from TVTropes (should probably consider removing other events
  as well).
  "
  [element]
  (if (= :span (first element))
    (let [attrs (second element)
          tail  (nthrest element 2)]
      (cond
        (= "notelabel" (:class attrs)) nil
        (map? attrs) (into [:span (dissoc attrs :id :onclick)] tail)
        :else element
        ))
    element))


(defn process-style
  "Assuming the attributes for an element contain a style key, and the value
  for this key is a string, it parses the string's values into a map. Otherwise
  we would get hiccup throwing an error before rendering it, which causes
  a problem for React."
  [element]
  (let [head  (first element)
        attrs (second element)
        tail  (nthrest element 2)
        style (:style attrs)]
    (if (and (not-empty style)
             (string? style))
      (into [head
             (assoc attrs :style (->> (split style #"\;")
                                      (map #(split % #"\:"))
                                      (into {}))
                          :onclick nil)]
            tail)
      element)
    ))



(defn process-element [processor element]
  "Applies a processor to an element, assuming that if it's a vector it'll be a hiccup structure."
  (if (vector? element)
    (processor element)
    element
    ))

(defn process-trope
  "Receives the hiccup data for a trope and processes it before display. See
  the comments on every one of the element processors for details."
  [coll extra-params]
  (let [element-processor (comp #(process-a % extra-params)
                                process-ul
                                process-style
                                process-span)]
    (clojure.walk/prewalk #(process-element element-processor %) coll)
    ))


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
        references  (re-frame/subscribe [:article-data :references])
        remaining   (reaction (count @references))]
    (fn []
      [:span
       [:div {:class "desc text-left"}
        [:p (process-trope (:hiccup @current-ref) [true])]]
       [button-item "Interesting" "btn-info btn-cta-secondary" [:vote :like] (empty? @current-ref) [:i {:class "fa fa-thumbs-o-up"}]]
       [button-item "Skip" "btn-default" [:vote :skip] (>= 0 @remaining)]
       [:p {:class "summary"} (str "(" @remaining " remaining)")]
       ])
    ))


(defn trope-reference-row
  "Receives a map with the information necessary to display atrope reference,
  and returns a component. Notice that ref is expected to be a hashmap, not an
  atom.
  "
  [{:keys [ref code display image]}]
  [:div {:class "item row"}
   [:a {:class "col-md-2 col-sm-2 col-xs-12" :on-click #(re-frame/dispatch [:load-article code false])}
    [:img {:class "img-responsive project-image" :src image}]]
   [:div {:class "desc col-md-10 col-sm-10 col-xs-12"}
    [:p (process-trope (:hiccup ref) [false])]
    [:p
     [:a {:class "more-link" :on-click #(re-frame/dispatch [:load-article code false])}
      [:li {:class "fa fa-external-link"}]
      display]]
    ]
   [button-item "Remove" "btn-danger pull-right to-bottom no-print" [:remove-like ref] false [:i {:class "fa fa-remove"}]]
   ]
  )

(defn like-list-display []
  (let [like-list (re-frame/subscribe [:article-data :like-list])]
    (fn []
      [:div
       (for [trope @like-list]
         ^{:key (hash trope)} [trope-reference-row trope])
       ])))

(defn graph-display []
  (let [show-graph? (re-frame/subscribe [:ui-state :show-graph?])
        like-list   (re-frame/subscribe [:article-data :like-list])
        code-list   (reaction (reduce union (map #(into #{(:code %)} (set (get-in % [:ref :links]))) @like-list)))]
    (fn []
      [:span
       [:div {:class "text-left"}
        [:a {:on-click #(re-frame/dispatch [:set-show-graph (not @show-graph?)])}
         (if @show-graph? "Hide" "Show")]]
       (if @show-graph?
         [:a {:on-click #(re-frame/dispatch [:draw-graph @code-list])}
          "Refresh"
          ]
         )
       (if @show-graph?
         [:div {:id "graph-area"}
          (re-frame/dispatch [:draw-graph @code-list])
          ])
       ]
      )
    )

  )

(defn error-list-display []
  (let [errors (re-frame/subscribe [:ui-state :errors])]
    (fn []
      (if @errors
        [:section {:class "section latest has-error form-group" :on-click #(re-frame/dispatch [:clear-errors])}
         [:div {:class "section-inner"}
          [:h2 {:class "heading"} "There seems to have been a problem..."]
          [:div {:class "content"}
           (for [error @errors]
             ; We can't do a hash of the message, since we may get the same message more than once.
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
  (reagent/render [graph-display] (.getElementById js/document "graph-container"))
  )
