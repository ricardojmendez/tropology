(ns tropology.test.base
  (:require [clojure.test :refer :all]
            [tropology.base :refer :all]))


(deftest test-label-from-id
         (are [id label] (is (= (category-from-code id) label))
              "Anime/CowboyBebop"     "Anime"
              "Film/TheMatrix"        "Film"
              "Some-Invalid-Format"   "Some-Invalid-Format"
              nil                     "Unknown"
              ""                      "Unknown"))



;
; Grouping helper
;


(deftest test-group-pairs
  (let [pairs [{:from-code 1 :to-code 2}
               {:from-code 1 :to-code 4}
               {:from-code 1 :to-code 3}
               {:from-code 2 :to-code 3}
               {:from-code 2 :to-code 4}
               {:from-code 3 :to-code 1}]
        g-from (group-pairs pairs)
        g-to   (group-pairs pairs :to-code :from-code)
        ]
    (are [from result] (= result (g-from from))
                       1 [2 4 3]
                       2 [3 4]
                       3 [1]
                       4 nil)
    (are [from result] (= result (g-to from))
                       1 [3]
                       2 [1]
                       3 [1 2]
                       4 [1 2])

    ))


(deftest test-display-from-url
  (are [display url] (= display (display-from-url url))
                     "Main/BigBad" "http://tvtropes.org/pmwiki/pmwiki.php/Main/BigBad"
                     "Manga/AhMyGoddess" "http://tvtropes.org/pmwiki/pmwiki.php/Manga/AhMyGoddess"
                     "Main/TakeMeInstead" "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInstead"
                     nil nil
                     ))

(deftest test-code-from-url
  (are [display url] (= display (code-from-url url))
                     "main/illgirl" "http://tvtropes.org/pmwiki/pmwiki.php/Main/IllGirl"
                     "anime/princesstutu" "http://tvtropes.org/pmwiki/pmwiki.php/Anime/PrincessTutu"
                     nil nil
                     ))

