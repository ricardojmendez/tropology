(ns tropefest.test.parsing
  (:require [clojure.test :refer :all]
            [tropefest.base :as b]
            [tropefest.parsing :refer :all]))

(deftest test-node-data-from-url
  (are [url result] (is (= (node-data-from-url url) result))
                    "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop" {:label "Anime" :id "Anime/CowboyBebop" :host "http://tvtropes.org/" :url "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop" :isRedirect false :hasError false}
                    "http://tvtropes.org/pmwiki/pmwiki.php/Main/HomePage" {:label "Main" :id "Main/HomePage" :host "http://tvtropes.org/" :url "http://tvtropes.org/pmwiki/pmwiki.php/Main/HomePage" :isRedirect false :hasError false}
                    "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix" {:label "Film" :id "Film/TheMatrix" :host "http://tvtropes.org/" :url "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix" :isRedirect false :hasError false}
                    "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix?q=1" {:label "Film" :id "Film/TheMatrix" :host "http://tvtropes.org/" :url "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix?q=1" :isRedirect false :hasError false}
                    "tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix" nil
                    "http://tvtropes.org/pmwiki/title_search_form.php" nil))


(def test-base-path (-> "." java.io.File. .getCanonicalPath))
(def test-file-path (str "file://" test-base-path "/files/"))


(deftest test-load-resource-url-local
  (let [name     (str test-file-path "CowboyBebop.html")
        result   (load-resource-url name)
        resource (:res result)
        url      (:url result)]
    (is (= (str test-file-path "CowboyBebop.html") url))    ; The originally requested URL is returned
    (is (= (nil? resource) false))
    (is (= (count resource) 2))
    (is (= (first resource) {:type :dtd, :data ["html" nil nil]}))))

(deftest test-node-data-from-meta
  (let [name      (str test-file-path "CowboyBebop.html")
        resource  (:res (load-resource-url name))
        node-data (node-data-from-meta resource)]
    (are [key result] (is (= (key node-data) result))
                      :url "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop"
                      :host "http://tvtropes.org/"
                      :title "Cowboy Bebop"
                      :image "http://static.tvtropes.org/pmwiki/pub/images/cowboy_bebop_lineup_7846.jpg"
                      :type "video.tv_show"
                      :id "Anime/CowboyBebop"
                      :label "Anime"
                      :hasError false
                      :isRedirect false)))

(deftest test-get-wiki-links
  (let [name      (str test-file-path "CowboyBebop.html")
        loaded    (load-resource-url name)
        node-data (-> loaded :res node-data-from-meta)
        links     (get-wiki-links (:res loaded) (:host node-data))]
    (is (= 653 (count links)))
    (is (= 653 (count (filter #(.startsWith % b/base-url) links)))))) ; All links start with the known base url



(deftest test-get-wiki-links
  (let [name      (str test-file-path "TakeMeInstead-pruned.html")
        loaded    (load-resource-url name)
        node-data (-> loaded :res node-data-from-meta)
        links     (get-wiki-links (:res loaded) (:host node-data))]
    (is (= 5 (count links)))
    (is (empty? (filter #(.startsWith % "External/LinkOutside") links))) ; Properly disregards link outside wikitext
    (is (= 5 (count (filter #(.startsWith % b/base-url) links)))))) ; All links start with the known base url