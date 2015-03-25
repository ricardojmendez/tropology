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


(defn in-seq? [s x]
  (some? (some #{x} s)))

(defn create-graph []
  (js/sigma.parsers.json "/api/network/Anime/SamuraiFlamenco"     ; "/static/test-data/basic-graph.json"
                         (clj->js {:container "container"
                                   :settings  {:defaultNodeColor "#ec5148"}})
                         (fn [s]
                           (goog.object/forEach (-> s .-graph .nodes)
                                                #(aset % "originalColor" "#ff0000"))
                           (.bind s "clickNode"
                                  (fn [clicked]
                                    (let [nodes (-> s .-graph .nodes) ; Re-bind in case it changed
                                          edges (-> s .-graph .edges)
                                          node-id (-> clicked .-data .-node .-id)
                                          nodes-to-keep (-> (.neighbors (.-graph s) node-id) (.concat node-id))
                                          groups (group-by #(in-seq? nodes-to-keep (.-id %)) nodes)]
                                      (doseq [node (groups true)] (aset node "color" "#ff0000"))
                                      (doseq [node (groups false)] (aset node "color" "#eee"))
                                      (.forEach edges       ; One idiomatic, one not as much
                                                (fn [edge]
                                                  (if (and
                                                        (in-seq? nodes-to-keep (.-source edge))
                                                        (in-seq? nodes-to-keep (.-target edge)))
                                                    (aset edge "color" "#ff0000")
                                                    (aset edge "color" "#eee"))))
                                      (.refresh s)))
                                  ))
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





