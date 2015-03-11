(ns com.numergent.tropefest-test
  (:require [clojure.test :refer :all]
            [com.numergent.tropefest :refer :all]))


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
