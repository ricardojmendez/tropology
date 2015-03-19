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
   [:input {:type "button" :value "Click me!"
            :on-click #(swap! click-count inc)}]])

(defn timer-component []
  (let [seconds-elapsed (atom 0)]
    (fn []
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div
       "Seconds Elapsed: " @seconds-elapsed]))) ()

(defn set-html! [el content]
  (set! (.-innerHTML el) content))



; Original code:

(defn navbar []
  [:div.navbar.navbar-inverse.navbar-fixed-top
   [:div.container
    [:div.navbar-header
     [:a.navbar-brand {:href "#/"} "tropefest"]]
    [:div.navbar-collapse.collapse
     [:ul.nav.navbar-nav
      [:li {:class (when (= :home (session/get :page)) "active")}
       [:a {:on-click #(secretary/dispatch! "#/")} "Home"]]
      [:li {:class (when (= :about (session/get :page)) "active")}
       [:a {:on-click #(secretary/dispatch! "#/about")} "About"]]]]]])

(defn about-page []
  [:div
   [:main]
   [:div "this is the story of tropefest... work in progress"]
   ]
  )

(defn home-page []
  [:div
   [:h2 "Welcome to ClojureScript"]])

(def pages
  {:home home-page
   :about about-page})

(defn page []
  [(pages (session/get :page))])

(defroute "/" [] (session/put! :page :home))
(defroute "/about" [] (session/put! :page :about))


(.log js/console "BEFORE INIT!")

(defn init! []
  (.log js/console "INITING!")
  (secretary/set-config! :prefix "#")
  (session/put! :page :home)
  (reagent/render-component [navbar] (.getElementById js/document "navbar"))
  (reagent/render-component [page] (.getElementById js/document "app")))



