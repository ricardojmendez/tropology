(ns tropology.test.api
  (:require [clojure.test :refer :all]
            [taoensso.timbre.profiling :as prof]
            [tropology.test.db-nodes :refer :all]
            [tropology.api :refer :all]
            [tropology.parsing :as p]
            [tropology.test.parsing :as tp]
            [tropology.api :as api]
            [tropology.db :as db]))

(defn create-test-network []
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
    [n1 n2 n3 n4 n5 n6 n7]
    )
  )


(deftest test-node-relationships
  (wipe-test-db)
  (create-test-network)
  ; Verify that we get the right nodes in a simple query
  (let [r     (node-relationships ["testnode/n1" "testnode/n2" "testnode/n6" "testnode/n7"])
        nodes (:nodes r)
        edges (:edges r)
        ]
    (is nodes)
    (is edges)
    (is (= 4 (count nodes)))
    (are [code] (some #(= (:id %) code) nodes)
                "testnode/n1"
                "testnode/n2"
                "testnode/n6"
                "testnode/n7")
    (is (= 3 (count edges)))
    (are [source target] (some #(and (= (:target %) target) (= (:source %) source)) edges)
                         "testnode/n1" "testnode/n2"
                         "testnode/n1" "testnode/n6"
                         "testnode/n6" "testnode/n7"))
  ; Verify DIFFREL isn't used by default
  (let [r     (node-relationships ["testnode/n1" "testnode/n3" "testnode/n4" "testnode/n6"])
        nodes (:nodes r)
        edges (:edges r)
        ]
    (is (= 4 (count nodes)))
    (are [code] (some #(= (:id %) code) nodes)
                "testnode/n1"
                "testnode/n3"
                "testnode/n4"
                "testnode/n6")
    (is (= 4 (count edges)))
    (are [source target] (some #(and (= (:target %) target) (= (:source %) source)) edges)
                         "testnode/n1" "testnode/n3"
                         "testnode/n1" "testnode/n6"
                         "testnode/n3" "testnode/n4"
                         "testnode/n4" "testnode/n6"))
  )




(deftest test-tropes-from-node
  (wipe-test-db)
  ; Test that we get the right amount of article references
  (testing "Confirm we get the right number of article references"
    (let [name (str tp/test-file-path "TakeMeInstead-retrieve-tropes.html")
          _    (p/record-page! name "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInstead")
          info (api/tropes-from-node "main/takemeinstead")
          ]
      (is (= 7 (count info)))
      (is (= "Take Me Instead - TV Tropes" (:title info)))
      (is (= "Main/TakeMeInstead" (:display info)))
      (is (= "main/takemeinstead" (:code info)))
      (is (= "http://static.tvtropes.org/logo_blue_small.png" (:image info)))
      (is (.startsWith (:description info) "A character offers"))
      (is (= 19 (count (:references info))))
      (doseq [item (:references info)]
        (is (vector? (:hiccup item)))                       ; Every trope refrence returned is a vector
        (is (keyword? (first (:hiccup item))))              ; and it starts with a keyword (for hiccup)
        (is (not-empty (:links item)))                      ; links contains a non-empty sequence
        )))
  (testing "Invalid code should return nil"
    (let [info (api/tropes-from-node "main/takemeinstead-er")]
      (is (nil? info))
      (println "Yes, we were supposed to get an AmazonS3Exception exception logged up there. ⤴")
      ))
  (testing "Receiving a slash as the code return a random trope"
    (let [info (api/tropes-from-node "/")]
      (is (:code info))))
  (testing "Receiving empty string as the code returns nil"
    (is (nil? (api/tropes-from-node "")))
    (is (nil? (api/tropes-from-node nil)))
    )
  )

(deftest test-tropes-from-redirect-node
  (wipe-test-db)
  (testing "When requesting the tropes for a redirector, we get the tropes for the one it redirects to"
    (let [name  (str tp/test-file-path "TakeMeInstead-retrieve-tropes.html")
          _     @(p/record-page! name "http://tvtropes.org/pmwiki/pmwiki.php/Main/RedirectMe")
          redir (db/query-by-code "main/redirectme")
          info  (api/tropes-from-node "main/redirectme")
          ]
      (is (:is-redirect redir))
      (is (= "main/takemeinstead" (:redirects-to redir)))
      (is (= 7 (count info)))
      (is (= "Take Me Instead - TV Tropes" (:title info)))
      (is (= "Main/TakeMeInstead" (:display info)))
      (is (= "main/takemeinstead" (:code info)))
      (is (= "http://static.tvtropes.org/logo_blue_small.png" (:image info)))
      (is (.startsWith (:description info) "A character offers"))
      (is (= 19 (count (:references info))))
      (doseq [item (:references info)]
        (is (vector? (:hiccup item)))                       ; Every trope refrence returned is a vector
        (is (keyword? (first (:hiccup item))))              ; and it starts with a keyword (for hiccup)
        (is (not-empty (:links item)))                      ; links contains a non-empty sequence
        )))
  )

