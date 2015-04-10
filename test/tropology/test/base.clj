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

