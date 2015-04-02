(ns tropefest.test.base
  (:require [clojure.test :refer :all]
            [tropefest.base :refer :all]))


(deftest test-label-from-id
         (are [id label] (is (= (label-from-id id) label))
              "Anime/CowboyBebop"     "Anime"
              "Film/TheMatrix"        "Film"
              "Some-Invalid-Format"   "Some-Invalid-Format"
              nil                     "Unknown"
              ""                      "Unknown"))

