(ns tropology.test.parsing
  (:require [clojure.test :refer :all]
            [tropology.base :as b]
            [tropology.parsing :refer :all]))


(deftest test-is-valid-url
  (are [url result]
    (is (= result (is-valid-url? url)))
    "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix" true
    "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix?Administrivia" true
    "http://tvtropes.org/pmwiki/pmwiki.php/Administrivia/1" false
    "http://tvtropes.org/pmwiki/pmwiki.php/Main/Administrivia" false
    "http://tvtropes.org/pmwiki/pmwiki.php/Main/Tropes" false
    "http://tvtropes.org/pmwiki/pmwiki.php/Tropers/Bubba" false
    "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop" true
    )
  )



(deftest test-node-data-from-url
  (are [url result]
    (is (= (node-data-from-url url) result))
    "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop" {:category "anime"  :code "anime/cowboybebop" :display "Anime/CowboyBebop" :url "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop" :is-redirect false :has-error false}
    "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix" {:category "film" :code "film/thematrix" :display "Film/TheMatrix" :url "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix" :is-redirect false :has-error false}
    "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix?q=1" {:category "film" :code "film/thematrix" :display "Film/TheMatrix" :url "http://tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix?q=1" :is-redirect false :has-error false}
    "tvtropes.org/pmwiki/pmwiki.php/Film/TheMatrix" nil
    "http://tvtropes.org/pmwiki/title_search_form.php" nil))


(def test-base-path (-> "." java.io.File. .getCanonicalPath))
(def test-file-path (str "file://" test-base-path "/files/"))


(deftest test-load-resource-url-local
  (let [name     (str test-file-path "CowboyBebop.html")
        result   (load-resource-url name)
        html     (:html result)
        resource (:res result)
        url      (:url result)]
    (is (= (str test-file-path "CowboyBebop.html") url))    ; The originally requested URL is returned
    (is (= (nil? resource) false))
    (is (= (count resource) 2))
    (is (= 317565 (.length html)))
    (is (= (first resource) {:type :dtd, :data ["html" nil nil]}))))

(deftest test-node-data-from-meta
  (let [name      (str test-file-path "CowboyBebop.html")
        resource  (:res (load-resource-url name))
        node-data (node-data-from-meta resource)]
    (are [key result] (is (= (key node-data) result))
                      :url "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop"
                      :title "Cowboy Bebop"
                      :image "http://static.tvtropes.org/pmwiki/pub/images/cowboy_bebop_lineup_7846.jpg"
                      :type "video.tv_show"
                      :code "anime/cowboybebop"
                      :display "Anime/CowboyBebop"
                      :category "anime"
                      :has-error false
                      :is-redirect false)))

(deftest test-get-wiki-links
  (let [name      (str test-file-path "CowboyBebop.html")
        loaded    (load-resource-url name)
        links     (get-wiki-links (:res loaded) b/base-host)]
    (is (= 650 (count links)))
    (is (= 650 (count (filter #(.startsWith % b/base-url) links)))))) ; All links start with the known base url



(deftest test-get-wiki-links-pruned
  (let [name      (str test-file-path "TakeMeInstead-pruned.html")
        loaded    (load-resource-url name)
        links     (get-wiki-links (:res loaded) b/base-host)]
    (is (= 4 (count links)))
    (is (empty? (filter #(.startsWith % "External/LinkOutside") links))) ; Properly disregards link outside wikitext
    (is (= 4 (count (filter #(.startsWith % b/base-url) links)))))) ; All links start with the known base url