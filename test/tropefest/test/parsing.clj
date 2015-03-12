(ns tropefest.test.parsing
  (:require [clojure.test :refer :all]
            [tropefest.parsing :refer :all]))


(deftest test-label-from-id
  (are [id label] (is (= (label-from-id id) label))
        "Anime/CowboyBebop"     "Anime"
        "Film/TheMatrix"        "Film"
        "Some-Invalid-Format"   "Some-Invalid-Format"
        ""                      "Unknown"))

(deftest test-node-data-from-url
  (are [url result] (is (= (node-data-from-url url) result))
       "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop"  {:label "Anime" :id "Anime/CowboyBebop" :host "http://tvtropes.org/" :url "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop"}
       "http://tvtropes.org/pmwiki/pmwiki.php/Main/HomePage"      {:label "Main" :id "Main/HomePage" :host "http://tvtropes.org/" :url "http://tvtropes.org/pmwiki/pmwiki.php/Main/HomePage"}
       "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix"     {:label "Film" :id "Film/TheMatrix" :host "http://tvtropes.org/" :url "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix"}
       "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix?q=1" {:label "Film" :id "Film/TheMatrix" :host "http://tvtropes.org/" :url "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix?q=1"}
       "tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix"            nil
       "http://tvtropes.org/pmwiki/title_search_form.php"         nil))


(def test-base-path (-> "." java.io.File. .getCanonicalPath))
(def test-file-path (str "file://" test-base-path "/files/"))


(deftest test-load-resource-url-local
  (let [name      (str test-file-path "CowboyBebop.html")
        resource  (load-resource-url name)]
    (is (= (nil? resource)    false))
    (is (= (count resource)   2))
    (is (= (first resource)   {:type :dtd, :data ["html" nil nil]}))))

(deftest test-node-data-from-meta
  (let [name      (str test-file-path "CowboyBebop.html")
        resource  (load-resource-url name)
        node-data (node-data-from-meta resource)]
    (are [key result] (is (= (key node-data) result))
      :url   "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop"
      :host  "http://tvtropes.org/"
      :title "Cowboy Bebop"
      :image "http://static.tvtropes.org/pmwiki/pub/images/cowboy_bebop_lineup_7846.jpg"
      :type  "video.tv_show"
      :id    "Anime/CowboyBebop"
      :label "Anime")))

(deftest test-get-wiki-links
  (let [name      (str test-file-path "CowboyBebop.html")
        resource  (load-resource-url name)
        node-data (node-data-from-meta resource)
        links     (get-wiki-links resource (:host node-data))]
    (is (= (count links) 732))
    (is (= (count (filter #(.startsWith % base-url) links)) 732)))) ; All links start with the known base url
