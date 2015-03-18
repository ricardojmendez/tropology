(ns tropefest.test.db-timestamp
  (:require [clojure.test :refer :all]
            [joda-time :as jt]
            [tropefest.db :refer :all]))



(deftest test-timestamp-create
  (let [data (timestamp-create {:a 1 :b 2.5})]
    (is (contains? data :timestamp))                        ; We add a new timestamp if one isn't present
    (is (contains? data :nextupdate))                       ; We add a new nextupdate if one isn't present
    (is (= (:a data) 1))                                    ; Confirm the existing values aren't touched
    (is (= (:b data) 2.5))
    (is (= (:timestamp data) (:nextupdate data)))))         ; timestamp and nextupdate should match on creation

(deftest test-timestamp-create-with-both-existing
  (let [data (timestamp-create {:nextupdate 20 :timestamp 1})]
    (is (contains? data :timestamp))                        ; We add a new timestamp if one isn't present
    (is (contains? data :nextupdate))                       ; We add a new nextupdate if one isn't present
    (is (< (:timestamp data) (:nextupdate data)))           ; timestamp and nextupdate should match on creation
    (is (= (:nextupdate data) 20))
    (is (= (:timestamp data) 1))))

(deftest test-timestamp-create-with-next-update-existing
  (let [data (timestamp-create {:nextupdate 20})]
    (is (contains? data :timestamp))                        ; We add a new timestamp if one isn't present
    (is (contains? data :nextupdate))                       ; We add a new nextupdate if one isn't present
    (is (> (:timestamp data) (:nextupdate data)))           ; timestamp and nextupdate should match on creation
    (is (= (:nextupdate data) 20))))


(deftest test-timestamp-update
  (let [now (.getMillis (jt/date-time))]
    (is (contains? (timestamp-update {}) :timestamp))       ; We add a new timestamp if one isn' present
    (is (= (:a (timestamp-update {:a 1.5 :timestamp now})) 1.5))
    (is (> (:timestamp (timestamp-update {})) now))
    (is (> (:timestamp (timestamp-update {:a 1.5 :timestamp now})) now))))


(deftest test-timestamp-next-update
  (let [data (timestamp-next-update {:a 1 :b 2.5})]
    (is (contains? data :timestamp))                        ; We add a new timestamp if one isn't present
    (is (contains? data :nextupdate))                       ; We add a new nextupdate if one isn't present
    (is (= (:a data) 1))                                    ; Confirm the existing values aren't touched
    (is (= (:b data) 2.5))
    (is (< (:timestamp data) (:nextupdate data)))))         ; nextupdate should be after timestamp
