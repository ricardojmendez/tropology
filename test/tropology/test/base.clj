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
  (let [pairs [{:from 1 :to 2}
               {:from 1 :to 4}
               {:from 1 :to 3}
               {:from 2 :to 3}
               {:from 2 :to 4}
               {:from 3 :to 1}]
        g-from (group-pairs pairs)
        g-to   (group-pairs pairs :to :from)
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

    )
  )