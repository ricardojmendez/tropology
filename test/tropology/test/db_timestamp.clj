(ns tropology.test.db-timestamp
  (:require [clojure.test :refer :all]
            [joda-time :as jt]
            [tropology.db :refer :all]))



(deftest test-timestamp-create
  (let [data (timestamp-create {:a 1 :b 2.5})]
    (is (contains? data :timeStamp))                        ; We add a new timestamp if one isn't present
    (is (contains? data :nextUpdate))                       ; We add a new nextupdate if one isn't present
    (is (= (:a data) 1))                                    ; Confirm the existing values aren't touched
    (is (= (:b data) 2.5))
    (is (= (:timeStamp data) (:nextUpdate data)))))         ; timestamp and nextupdate should match on creation

(deftest test-timestamp-create-with-both-existing
  (let [data (timestamp-create {:nextUpdate 20 :timeStamp 1})]
    (is (contains? data :timeStamp))                        ; We add a new timestamp if one isn't present
    (is (contains? data :nextUpdate))                       ; We add a new nextupdate if one isn't present
    (is (< (:timeStamp data) (:nextUpdate data)))           ; timestamp and nextupdate should match on creation
    (is (= (:nextUpdate data) 20))
    (is (= (:timeStamp data) 1))))

(deftest test-timestamp-create-with-next-update-existing
  (let [data (timestamp-create {:nextUpdate 20})]
    (is (contains? data :timeStamp))                        ; We add a new timestamp if one isn't present
    (is (contains? data :nextUpdate))                       ; We add a new nextupdate if one isn't present
    (is (> (:timeStamp data) (:nextUpdate data)))           ; timestamp and nextupdate should match on creation
    (is (= (:nextUpdate data) 20))))


(deftest test-timestamp-update
  (let [now (.getMillis (jt/date-time))]
    (is (contains? (timestamp-update {}) :timeStamp))       ; We add a new timestamp if one isn' present
    (is (= (:a (timestamp-update {:a 1.5 :timeStamp now})) 1.5))
    (is (> (:timeStamp (timestamp-update {})) now))
    (is (> (:timeStamp (timestamp-update {:a 1.5 :timeStamp now})) now))))


(deftest test-timestamp-next-update
  (let [data (timestamp-next-update {:a 1 :b 2.5})]
    (is (contains? data :timeStamp))                        ; We add a new timestamp if one isn't present
    (is (contains? data :nextUpdate))                       ; We add a new nextupdate if one isn't present
    (is (= (:a data) 1))                                    ; Confirm the existing values aren't touched
    (is (= (:b data) 2.5))
    (is (< (:timeStamp data) (:nextUpdate data)))))         ; nextupdate should be after timestamp
