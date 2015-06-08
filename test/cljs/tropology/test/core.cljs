(ns tropology.test.core
  (:require [cljs.test :refer-macros [deftest testing is]]))

(deftest hello-world
         (println "Hello world!")
         (is (= true true)))

(deftest hello-world-2
         (println "Hello world 2!")
         (is (= 1 1)))

