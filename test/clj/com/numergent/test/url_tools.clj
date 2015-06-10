(ns com.numergent.test.url-tools
  (:require [clojure.test :refer :all]
            [com.numergent.url-tools :refer :all]))

(deftest if-empty-tests
  (testing "Test cases for if-empty"
    (is (= (if-empty nil "b")   "b"))
    (is (= (if-empty "" "b")    "b"))
    (is (= (if-empty "a" "b")   "a"))))

(deftest host-string-of-tests
  (testing "Basic test cases for host-string-of"
    (is (= (host-string-of "http://tvtropes.org")               "http://tvtropes.org/"))
    (is (= (host-string-of "http://tvtropes.org/")              "http://tvtropes.org/"))
    (is (= (host-string-of "http://tvtropes.org/somePath")      "http://tvtropes.org/"))
    (is (= (host-string-of "http://tvtropes.org/somePath?q=1")  "http://tvtropes.org/"))
    (is (= (host-string-of "https://tvtropes.org/somePath?q=1") "https://tvtropes.org/"))))

(deftest url-without-query-tests
    (is (= (without-query "http://tvtropes.org")               "http://tvtropes.org/"))
    (is (= (without-query "http://tvtropes.org/")              "http://tvtropes.org/"))
    (is (= (without-query "http://tvtropes.org/somePath")      "http://tvtropes.org/somePath"))
    (is (= (without-query "http://tvtropes.org/somePath?q=1")  "http://tvtropes.org/somePath"))
    (is (= (without-query "https://tvtropes.org/somePath?q=1") "https://tvtropes.org/somePath"))
  )