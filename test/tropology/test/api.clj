(ns tropology.test.api
  (:require [clojure.test :refer :all]
            [korma.core :refer :all]
            [taoensso.timbre.profiling :as prof]
            [tropology.test.db-nodes :refer :all]
            [tropology.api :refer :all]
            [tropology.parsing :as p]
            [tropology.test.parsing :as tp]
            [tropology.api :as api]))

(deftest test-network-from-node
  (wipe-test-db)
  (let [n1 (create-node! (-> (basic-test-node "TestNode/N1") (assoc :incoming 100)))
        n2 (create-node! (-> (basic-test-node "TestNode/N2") (assoc :incoming 100)))
        n3 (create-node! (-> (basic-test-node "TestNode/N3") (assoc :incoming 100)))
        n4 (create-node! (-> (basic-test-node "TestNode/N4") (assoc :incoming 100)))
        n5 (create-node! (-> (basic-test-node "TestNode/N5") (assoc :incoming 100)))
        n6 (create-node! (-> (basic-test-node "TestNode/N6") (assoc :incoming 100)))
        n7 (create-node! (-> (basic-test-node "TestNode/N7") (assoc :incoming 100)))
        _  (relate-nodes! :LINKSTO n1 n2)
        _  (relate-nodes! :LINKSTO n1 n3)
        _  (relate-nodes! :LINKSTO n1 n5)
        _  (relate-nodes! :LINKSTO n1 n6)
        _  (relate-nodes! :LINKSTO n2 n3)
        _  (relate-nodes! :LINKSTO n2 n4)
        _  (relate-nodes! :LINKSTO n3 n4)
        _  (relate-nodes! :LINKSTO n3 n5)
        _  (relate-nodes! :LINKSTO n4 n5)
        _  (relate-nodes! :LINKSTO n4 n6)
        _  (relate-nodes! :LINKSTO n5 n6)
        _  (relate-nodes! :LINKSTO n6 n7)
        _  (relate-nodes! :DIFFREL n1 n4)                   ; To be excluded in tests below
        _  (relate-nodes! :DIFFREL n1 n3)                   ; To be excluded in tests below
        _  (relate-nodes! :DIFFREL n3 n4)                   ; To be excluded in tests below
        ]
    ; Test relationships
    (let [r     (network-from-node "testnode/n1")
          nodes (:nodes r)
          edges (:edges r)
          ]
      (is nodes)
      (is edges)
      (is (= 5 (count nodes)))
      (are [code] (some #(= (:id %) code) nodes)
                  "testnode/n1"
                  "testnode/n2"
                  "testnode/n3"
                  "testnode/n5"
                  "testnode/n6")
      (is (= 7 (count edges)))
      (are [source target] (some #(and (= (:target %) target) (= (:source %) source)) edges)
                           "testnode/n1" "testnode/n2"
                           "testnode/n1" "testnode/n3"
                           "testnode/n1" "testnode/n5"
                           "testnode/n1" "testnode/n6"
                           "testnode/n2" "testnode/n3"
                           "testnode/n3" "testnode/n5"
                           "testnode/n5" "testnode/n6"
                           ))

    ))


(deftest test-tropes-from-node
  (wipe-test-db)
  ; Test that we get the right amount of article references
  (let [name (str tp/test-file-path "TakeMeInstead-retrieve-tropes.html")
        _    (p/record-page! name "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInstead")
        info (api/tropes-from-node "main/takemeinstead")
        ]
    (is (= 3 (count info)))
    (is (= "Take Me Instead - TV Tropes" (:title info)))
    (is (.startsWith (:description info) "A character offers"))
    (is (= 19 (count (:tropes info))))
    (doseq [trope (:tropes info)]
      (is (vector? trope))                                  ; Every trope refrence returned is a vector
      (is (keyword? (first trope)))                         ; and it starts with a keyword (for hiccup)
      ))
  (let [info (api/tropes-from-node "main/takemeinstead-er")
        ]
    (is (nil? info)))
  )