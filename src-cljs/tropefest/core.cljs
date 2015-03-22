(ns tropefest.core
  (:require [reagent.core :as reagent :refer [atom]]
            [secretary.core :as secretary]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields]]
            [ajax.core :refer [POST]])
  (:require-macros [secretary.core :refer [defroute]]))



; Some example functions from http://holmsand.github.io/reagent/
; Currently using them to test reagent via figwheel
;
; For instance:
;
; (def e (aget (js/document.getElementsByTagName "main") 0))
; (reagent/render [c/timer-component] e)
; (reagent/render [c/counting-component] e)

(def click-count (atom 0))
(def seconds-elapsed (atom 0))

(defn counting-component []
  [:div
   "The atom " [:code "click-count"] " has value: "
   @click-count ". "
   [:input {:type     "button" :value "Click me!"
            :on-click #(swap! click-count inc)}]])

(defn timer-component []
  (let [seconds-elapsed (atom 0)]
    (fn []
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div
       "Seconds Elapsed: " @seconds-elapsed])))
()

(defn set-html! [el content]
  (set! (.-innerHTML el) content))



; Original code:

(defn nav-item [tag label url]
  [:li {:class (when (= tag (session/get :page)) "active")}
   [:a {:on-click #(secretary/dispatch! (str "#/" url))} label]])

(defn navbar []
  [:div.navbar.navbar-inverse.navbar-fixed-top
   [:div.container
    [:div.navbar-header
     [:a.navbar-brand {:href "#/"} "tropefest"]]
    [:div.navbar-collapse.collapse
     [:ul.nav.navbar-nav
      (nav-item :home "Home" "")
      (nav-item :about "About" "about")
      (nav-item :plot "Plot" "plot")
      ]]]])

(defn about-page []
  [:div
   [:main]
   [:div "this is the story of tropefest... work in progress"]
   ]
  )

(defn home-page []
  [:div
   [:h2 "Welcome to ClojureScript"]])

; Graph!

(-> js/sigma .-classes .-graph (.addMethod "neighbors",
                                           (fn [node-id]
                                             (-> (js* "this")
                                                 .-allNeighborsIndex
                                                 (aget node-id)
                                                 goog.object/getKeys)) ; The graph keeps the neighbors as properties
                                           ))

(defn create-graph []
  (js/sigma.parsers.json "/static/test-data/basic-graph.json"
                         (clj->js {:container "container"
                                   :settings  {:defaultNodeColor "#ec5148"}})
                         (fn [s]
                           (.log js/console s)
                           (.log js/console (-> s .-graph .nodes))
                           (map #(.log js/console %) (-> s .-graph .nodes))
                           (.bind s "clickNode"
                                  (fn [clicked]
                                    (let [node-id (-> clicked .-data .-node .-id)
                                          to-keep (.neighbors (.-graph s) (-> clicked .-data .-node .-id))]
                                      (.log js/console (str "ID " node-id))
                                      (.log js/console to-keep))
                                    (.log js/console (str "Click: " clicked))))
                           (map (fn [n1] (.log js/console (str "N: " n1))) (-> s .-graph .nodes))
                           ;(.graph.nodes.forEach s (fn [n1] (.log js/console (str "N: " n1))))
                           ;(.graph.edges.forEach s (fn [e1] (.log js/console (str "E: " e1))))
                           ;(.bind "clickNode" s (fn [clicked] (.log js/console (str "Click: " clicked))))
                           )
                         ))


(defn plot []
  [:div
   [:input {:type     "button" :value "Graph!"
            :on-click #(create-graph)}]
   [:div {:id "container"}]])

(def pages
  {:home  home-page
   :about about-page
   :plot  plot})




(defn page []
  [(pages (session/get :page))])

(defroute "/" [] (session/put! :page :home))
(defroute "/about" [] (session/put! :page :about))
(defroute "/plot" [] (session/put! :page :plot))
(defroute "/plot" [] (session/put! :page :plot))


(defn init! []
  (secretary/set-config! :prefix "#")
  (session/put! :page :home)
  (reagent/render-component [navbar] (.getElementById js/document "navbar"))
  (reagent/render-component [page] (.getElementById js/document "app")))





