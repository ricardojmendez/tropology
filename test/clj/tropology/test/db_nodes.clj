(ns tropology.test.db-nodes
  (:refer-clojure :exclude [update])
  (:require [clojure.test :refer :all]
            [korma.core :refer :all]
            [taoensso.timbre.profiling :as prof]
            [tropology.db :refer :all]
            [tropology.base :as b]
            [clojure.string :as s]
            [tropology.db :as db]
            [tropology.parsing :as p]
            [tropology.test.parsing :as tp]))

(defn wipe-test-db []
  (delete contents)
  (delete links)
  (delete pages))


;
; Test node creation and tagging
;

(defn sanitize-test-data [data]
  (let [code (:code data)]
    (->> (merge data {:code (s/lower-case code)})
         (merge {:display code}))))


(defn create-node!
  "Creates a node directly."
  [data-items]
  (let [data (timestamp-create (sanitize-test-data data-items))
        _    (db/save-page! data)]
    (db/query-by-code (:code data))))


(defn relate-nodes!
  "Links two nodes by a relationship"
  [rel n1 n2]
  (insert db/links (values {:from-code (:code n1) :to-code (:code n2) :type (name rel)})))



;
; Helper functions
;

(defn basic-test-node [code]
  {:code (s/lower-case code) :display code :title "Test node" :url "http://localhost/" :type "Test"})


(defn get-all-articles []
  (->> (select pages)
       (map db/rename-db-keywords)
       ))

(defn get-all-article-rels []
  (->> (select links)
       (map db/rename-db-keywords)))

(defn get-all-contents []
  (select contents))

;
; Tests
;



(deftest test-create-node
  (wipe-test-db)
  (let [node (create-node! {:code "TestNode/First" :next-update 5 :url "http://localhost/"})]
    (is (not= node nil))
    (are [path result] (= (get-in node path) result)
                       [:code] "testnode/first"
                       [:next-update] 5
                       [:url] "http://localhost/")))


(deftest test-create-page
  (wipe-test-db)
  ; Test single page creation
  (let [_    (create-page-and-links! (basic-test-node "TestNode/First"))
        all  (get-all-articles)
        item (first all)]
    (is (= 1 (count all)))
    (are [k v] (= v (k item))
               :code "testnode/first"
               :display "TestNode/First"
               :title "Test node"
               :url "http://localhost/"
               :type "Test"
               ; Review defaults
               :category nil
               :image nil
               :has-error false
               :is-redirect false))
  ; Test creating linked nodes
  (let [linked ["L/N1" "L/N2" "L/N3" "L/N4"]
        urls   (map #(str b/base-url %) linked)
        _      (create-page-and-links! (basic-test-node "TestNode/Links")
                                       :LINKSTO
                                       (map p/node-data-from-url urls)
                                       {:is-redirect false})
        all    (get-all-articles)
        item   (first (filter #(= "TestNode/Links" (:display %)) all))]
    (is (= 6 (count all)))                                  ; Five we created, and the existing one
    (doseq [k linked]
      (is (not-empty (filter #(= (s/lower-case k) (:code %)) all))))
    (are [k v] (= v (k item))
               :code "testnode/links"
               :display "TestNode/Links"
               :title "Test node"
               :url "http://localhost/"
               :type "Test"
               ; Review defaults
               :category nil
               :image nil
               :has-error false
               :is-redirect false))
  )


(deftest test-create-node-assigns-timestamps
  (wipe-test-db)
  (let [node (create-node! (basic-test-node "TestNode/First"))]
    (is (not= node nil))
    (are [path] (> (get-in node path) 0)
                [:time-stamp]
                [:next-update])))


(deftest test-query-by-code
  (wipe-test-db)
  (is (nil? (query-by-code "TestNode/ForQuerying")))
  (is (nil? (query-by-code nil)))
  (let [_    (create-page-and-links! (basic-test-node "TestNode/ForQuerying"))
        node (query-by-code "TestNode/ForQuerying")]
    (is (not= node nil))
    (are [path result] (= result (node path))
                       :code "testnode/forquerying"
                       :display "TestNode/ForQuerying"
                       )))


(deftest test-get-html
  (wipe-test-db)
  (insert contents (values {:code "test-code" :html "Some HTML message that isn't very long"}))
  (is (= 38 (count (get-html "test-code"))))
  (is (nil? (get-html "invalid-code"))))


(deftest test-query-nodes-when-empty
  (wipe-test-db)
  (is (= (count (query-nodes-to-crawl 100)) 0))
  (is (= (query-nodes-to-crawl 100) '())))


(defn create-nodes!
  ([n is-redirect]
   (create-nodes! n is-redirect 0))
  ([n is-redirect base-n]
   (dotimes [s n]
     (let [i (+ s base-n)]
       (create-node! {:code        (str "TestNode/" i)
                      :display     (str "TestNode/" i)
                      :next-update i
                      :url         (str i)                  ; Keep the i as the url for ease of testing
                      :type        "Test"
                      :title       (str "Test node " i)
                      :is-redirect is-redirect
                      :has-error   false})))))

(defn create-contents!
  ([n file-path]
   (create-contents! n file-path 0))
  ([n file-path base-n]
   (let [html (slurp (str tp/test-file-path file-path))]
     (dotimes [s n]
       (db/save-page-contents! {:code (str "TestNode/" (+ s base-n))
                                :html html})
       ))))


(deftest test-fetch-random-code
  (wipe-test-db)
  (create-nodes! 50 false)
  (create-contents! 10 "TakeMeInstead-pruned.html")
  (is (= 10 (count (get-all-contents))))
  (is (= 50 (count (get-all-articles))))
  (dotimes [_ 100]
    ; Run this a few times to ensure we can always get a random code
    (is (not-empty (db/fetch-random-contents-code)))
    ))


(deftest test-query-nodes-node-limit
  (wipe-test-db)
  (create-nodes! 30 false)                                  ; Create non-redirect nodes
  (is (= 30 (count (query-nodes-to-crawl 100))))
  (is (= 15 (count (query-nodes-to-crawl 15))))
  (is (= (query-nodes-to-crawl 0) '())))


(deftest test-query-nodes-node-skip-errors
  (wipe-test-db)
  (create-nodes! 30 false)
  (log-error! {:code "TestNode/10" :url "10" :has-error true :error "Oopsy"})
  (is (= (count (query-nodes-to-crawl 100)) 29))            ; We skip the error node
  (is (= (count (query-nodes-to-crawl 15)) 15))             ; We can still find 15 nodes to crawl
  )


(deftest test-query-nodes-time-limit
  (wipe-test-db)
  (create-nodes! 15 false)
  (create-nodes! 3 true 20)
  (dotimes [i 16]
    (is (= (count (query-nodes-to-crawl 100 i)) i)))        ; The number of nodes where the nextupdate time is under i is the i itself
  (is (= (count (query-nodes-to-crawl 100 20)) 15))         ; All nodes are older than 100, we should get all
  (is (every?
        #(< (Integer. %) 5)                                 ; We were keeping in the URL, which will be the returned value, the same limit...
        (query-nodes-to-crawl 20 5)))                       ; ... so we can check every node returned is indeed under
  )


(deftest test-query-nodes-sort-order
  (wipe-test-db)
  (create-nodes! 20 false)
  (dotimes [i 16]
    ; We are going to limit the number of nodes every time we query.
    ; Given that the nextupdate is used as the url (for this test), and
    ; that the nodes should be sorted by nextupdate, every url returned
    ; should be lower than the limit
    (is (every?
          #(< (Integer. %) i)
          (query-nodes-to-crawl i)))
    ))


(deftest test-relate-nodes
  (wipe-test-db)
  (let [n1  (create-node! (basic-test-node "TestNode/N1"))
        n2  (create-node! (basic-test-node "TestNode/N2"))
        rel (relate-nodes! :LINKSTO n1 n2)]
    (is (not= rel nil))
    (are [query result] (= query result)
                        (:type rel) "LINKSTO"
                        (:start rel) (:location-uri n1)
                        (:end rel) (:location-uri n2))
    ))


(deftest test-query-from
  (wipe-test-db)
  (let [n1 (create-node! (basic-test-node "TestNode/N1"))
        n2 (create-node! (basic-test-node "TestNode/N2"))
        n3 (create-node! (basic-test-node "TestNode/N3"))
        _  (relate-nodes! :LINKSTO n1 n2)
        _  (relate-nodes! :LINKSTO n1 n3)
        _  (relate-nodes! :LINKSTO n2 n3)
        ]
    (let [r (query-from "TestNode/N1" :LINKSTO)]
      (is (= (count r) 2))
      (is (some #(= (:display %) "TestNode/N2") r))
      (is (some #(= (:display %) "TestNode/N3") r)))
    (let [r (query-from "TestNode/N2" :LINKSTO)]
      (is (= (count r) 1))
      (is (some #(= (:display %) "TestNode/N3") r)))
    (let [r (query-from "TestNode/N3" :LINKSTO)]
      (is (empty? r)))
    ))

(deftest test-query-to
  (wipe-test-db)
  (let [n1 (create-node! (basic-test-node "TestNode/N1"))
        n2 (create-node! (basic-test-node "TestNode/N2"))
        n3 (create-node! (basic-test-node "TestNode/N3"))
        _  (relate-nodes! :LINKSTO n1 n2)
        _  (relate-nodes! :LINKSTO n1 n3)
        _  (relate-nodes! :LINKSTO n2 n3)
        ]
    (let [r (query-to :LINKSTO "TestNode/N3")]
      (is (= (count r) 2))
      (is (some #(= (:display %) "TestNode/N1") r))
      (is (some #(= (:display %) "TestNode/N2") r)))
    (let [r (query-to :LINKSTO "TestNode/N2")]
      (is (= (count r) 1))
      (is (some #(= (:display %) "TestNode/N1") r)))
    (let [r (query-to :LINKSTO "TestNode/N1")]
      (is (empty? r)))
    ))

(deftest test-common-from
  (wipe-test-db)
  (let [n1 (create-node! (-> (basic-test-node "TestNode/N1") (assoc :incoming 100)))
        n2 (create-node! (-> (basic-test-node "TestNode/N2") (assoc :incoming 500))) ; To test limits later
        n3 (create-node! (-> (basic-test-node "TestNode/N3") (assoc :incoming 100)))
        n4 (create-node! (-> (basic-test-node "TestNode/N4") (assoc :incoming 100)))
        n5 (create-node! (-> (basic-test-node "TestNode/N5") (assoc :incoming 100)))
        n6 (create-node! (-> (basic-test-node "TestNode/N6") (assoc :incoming 100)))
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
        _  (relate-nodes! :DIFFREL n1 n4)                   ; To be excluded in most tests below
        _  (relate-nodes! :DIFFREL n1 n3)                   ; To be excluded in most tests below
        _  (relate-nodes! :DIFFREL n3 n4)                   ; To be excluded in most tests below
        ]
    ; Test relationships
    (let [r (query-common-nodes-from "TestNode/N3")]
      (is (= 4 (count r)))                                  ; N1 links out to N2 and N5, and both are related to N3. So is N4.
      (are [from to] (some #(and (= (:to-code %) to) (= (:from-code %) from)) r)
                     "testnode/n1" "testnode/n2"
                     "testnode/n1" "testnode/n5"
                     "testnode/n2" "testnode/n4"
                     "testnode/n4" "testnode/n5"))
    (let [r (query-common-nodes-from "TestNode/N1")]
      (is (= 2 (count r)))
      (are [from to] (some #(and (= (:to-code %) to) (= (:from-code %) from)) r)
                     "testnode/n2" "testnode/n3"
                     "testnode/n3" "testnode/n5"))
    ; Test incoming limits
    (let [r (query-common-nodes-from "TestNode/N3" :LINKSTO 400)]
      (is (= 2 (count r)))                                  ; N2 is excluded because of too many incoming links
      (are [from to] (some #(and (= (:to-code %) to) (= (:from-code %) from)) r)
                     "testnode/n1" "testnode/n5"
                     "testnode/n4" "testnode/n5")
      )
    ; Test link relationship types
    (let [r (query-common-nodes-from "TestNode/N3" :DIFFREL 1000)]
      (is (= 1 (count r)))                                  ; Only one node in common with that relationship type
      (is (some #(= (:to-code %) "testnode/n4") r)))

    ))

