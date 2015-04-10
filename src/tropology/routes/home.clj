(ns tropology.routes.home
  (:require [tropology.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [clojure.java.io :as io]))

(defn home-page []
  (layout/render
    "home.html" {:docs (-> "docs/readme.md" io/resource slurp)}))

(defn about-page []
  (layout/render "about.html"))

(defroutes home-routes
           (GET "/" [] (home-page))
           (GET "/about" [] (about-page))
           (route/resources "/static")
           (route/not-found "<h1>Page not found</h1>"))
