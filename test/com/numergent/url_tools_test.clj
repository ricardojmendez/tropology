(ns com.numergent.url-tools-test
  (:require [clojure.test :refer :all]
            [com.numergent.url-tools :refer :all]))

(deftest if-empty-tests
  (testing "Test cases for if-empty"
    (is (= (if-empty nil "b")   "b"))
    (is (= (if-empty "" "b")    "b"))
    (is (= (if-empty "a" "b")   "a"))))


(deftest if-nil-tests
  (testing "Test cases for if-nil"
    (is (= (if-nil nil "b")       "b"))
    (is (= (if-nil {} {:a 1})     {}))
    (is (= (if-nil {:a 2} {:a 1}) {:a 2}))
    (is (= (if-nil nil {:a 1})    {:a 1}))))


(deftest host-string-of-tests
  (testing "Basic test cases for host-string-of"
    (is (= (host-string-of "http://tvtropes.org")               "http://tvtropes.org/"))
    (is (= (host-string-of "http://tvtropes.org/")              "http://tvtropes.org/"))
    (is (= (host-string-of "http://tvtropes.org/somePath")      "http://tvtropes.org/"))
    (is (= (host-string-of "http://tvtropes.org/somePath?q=1")  "http://tvtropes.org/"))
    (is (= (host-string-of "https://tvtropes.org/somePath?q=1") "https://tvtropes.org/"))))