(ns tropefest.test.db-nodes
  (:require [clojure.test :refer :all]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [joda-time :as jt]
            [tropefest.db :refer :all]))

(defn get-test-connection []
  (nr/connect "http://192.168.59.103:7373/db/data/"))


(defn wipe-test-db []
  (let [conn (get-test-connection)]
    (cy/tquery conn "MATCH (n)-[r]-(m) DELETE n,r,m")       ; Delete notes with relationships
    (cy/tquery conn "MATCH (n) DELETE n")                   ; Delete singletons
    ))


(deftest test-id-to-match
  (is (= (id-to-match "pref" "Label/Name") "(pref:Label {id:{id}})"))
  (is (= (id-to-match "pref" "L/N" "idx") "(pref:L {id:{idx}})"))
  (is (= (id-to-match "l" "N/Id") "(l:N {id:{id}})")))



(deftest test-create-node
  (wipe-test-db)
  (let [node (create-node! (get-test-connection) "TestNode" {:id "TestNode/First" :nextupdate 5 :url "http://localhost/"})]
    (is (not= node nil))
    (are [path result] (= (get-in node path) result)
                       [:data :id] "TestNode/First"
                       [:data :nextupdate] 5
                       [:data :url] "http://localhost/")))

(deftest test-create-node-assigns-timestamps
  (wipe-test-db)
  (let [node (create-node! (get-test-connection) "TestNode" {:id "TestNode/First"})]
    (is (not= node nil))
    (are [path] (> (get-in node path) 0)
                [:data :timestamp]
                [:data :nextupdate])))

(deftest test-query-by-id
  (wipe-test-db)
  (let [conn (get-test-connection)]
    (is (= (query-by-id conn "TestNode/ForQuerying") nil))
    (create-node! conn "TestNode" {:id "TestNode/ForQuerying"})
    (let [node (query-by-id conn "TestNode/ForQuerying")]
      (is (not= node nil))
      (are [path result] (= (get-in node path) result)
                         [:metadata :labels] ["TestNode"]
                         [:data :id] "TestNode/ForQuerying"
                         ))))


(deftest test-query-nodes-when-empty
  (wipe-test-db)
  (is (= (count (query-nodes-to-crawl (get-test-connection) 100)) 0))
  (is (= (query-nodes-to-crawl (get-test-connection) 100) '())))


(defn create-test-nodes [n isredirect]
  (dotimes [i n]
    (create-node! (get-test-connection) "TestNode" {:id         (str "TestNode/" i)
                                                    :nextupdate i
                                                    :url        (str i) ; Keep the i as the url for ease of testing
                                                    :isredirect isredirect})))

(deftest test-query-nodes-node-limit
  (wipe-test-db)
  (create-test-nodes 30 false)                              ; Create non-redirect nodes
  (is (= (count (query-nodes-to-crawl (get-test-connection) 100)) 30))
  (is (= (count (query-nodes-to-crawl (get-test-connection) 15)) 15))
  (is (= (query-nodes-to-crawl (get-test-connection) 0) '())))


(deftest test-query-nodes-node-skip-errors
  (wipe-test-db)
  (create-test-nodes 30 false)
  (create-or-merge-node! (get-test-connection) {:id "TestNode/10" :label "TestNode" :error "Oopsy"})
  (is (= (count (query-nodes-to-crawl (get-test-connection) 100)) 29)) ; We skip the error node
  (is (= (count (query-nodes-to-crawl (get-test-connection) 15)) 15)) ; We can still find 15 nodes to crawl
  )


(deftest test-query-nodes-time-limit
  (wipe-test-db)
  (create-test-nodes 15 false)
  (create-test-nodes 3 true)
  (dotimes [i 16]
    (is (= (count (query-nodes-to-crawl (get-test-connection) 100 i)) i))) ; The number of nodes where the nextupdate time is under i is the i itself
  (is (= (count (query-nodes-to-crawl (get-test-connection) 100 20)) 15)) ; All nodes are older than 100, we should get all
  (is (every?
        #(< (Integer. %) 5)                                 ; We were keeping in the URL, which will be the returned value, the same limit...
        (query-nodes-to-crawl (get-test-connection) 20 5))) ; ... so we can check every node returned is indeed under
  )

(deftest test-query-nodes-sort-order
  (wipe-test-db)
  (create-test-nodes 20 false)
  (dotimes [i 16]
    ; We are going to limit the number of nodes every time we query.
    ; Given that the nextupdate is used as the url (for this test), and
    ; that the nodes should be sorted by nextupdate, every url returned
    ; should be lower than the limit
    (is (every?
          #(< (Integer. %) i)
          (query-nodes-to-crawl (get-test-connection) i)))
    ))


(deftest test-relate-nodes
  ; We don't wipe the db to ensure association works even if there were previous nodes
  (let [conn (get-test-connection)
        n1 (create-node! conn "TestNode" {:id "TestNode/N1"})
        n2 (create-node! conn "TestNode" {:id "TestNode/N2"})
        rel (relate-nodes! conn :LINKSTO n1 n2)]
    (is (not= rel nil))
    (are [query result] (= query result)
                        (:type rel) "LINKSTO"
                        (:start rel) (:location-uri n1)
                        (:end rel) (:location-uri n2))
    ))


(deftest test-query-from
  (let [conn (get-test-connection)
        n1 (create-node! conn "TestNode" {:id "TestNode/N1"})
        n2 (create-node! conn "TestNode" {:id "TestNode/N2"})
        n3 (create-node! conn "TestNode" {:id "TestNode/N3"})
        _ (relate-nodes! conn :LINKSTO n1 n2)
        _ (relate-nodes! conn :LINKSTO n1 n3)
        _ (relate-nodes! conn :LINKSTO n2 n3)
        ]
    (let [r (query-from conn "TestNode/N1" :LINKSTO)]
      (is (= (count r) 2))
      (is (some #(= (% "id") "TestNode/N2") r))
      (is (some #(= (% "id") "TestNode/N3") r)))
    (let [r (query-from conn "TestNode/N2" :LINKSTO)]
      (is (= (count r) 1))
      (is (some #(= (% "id") "TestNode/N3") r)))
    (let [r (query-from conn "TestNode/N3" :LINKSTO)]
      (is (empty? r)))
    ))

(deftest test-query-to
  (let [conn (get-test-connection)
        n1 (create-node! conn "TestNode" {:id "TestNode/N1"})
        n2 (create-node! conn "TestNode" {:id "TestNode/N2"})
        n3 (create-node! conn "TestNode" {:id "TestNode/N3"})
        _ (relate-nodes! conn :LINKSTO n1 n2)
        _ (relate-nodes! conn :LINKSTO n1 n3)
        _ (relate-nodes! conn :LINKSTO n2 n3)
        ]
    (let [r (query-to conn :LINKSTO "TestNode/N3")]
      (is (= (count r) 2))
      (is (some #(= (% "id") "TestNode/N1") r))
      (is (some #(= (% "id") "TestNode/N2") r)))
    (let [r (query-to conn :LINKSTO "TestNode/N2")]
      (is (= (count r) 1))
      (is (some #(= (% "id") "TestNode/N1") r)))
    (let [r (query-to conn :LINKSTO "TestNode/N1")]
      (is (empty? r)))
    ))




(deftest test-merge-node
  ; No need to wipe the db
  (let [conn (get-test-connection)
        node (create-node! conn "TestNode" {:id "TestNode/First" :nextupdate 5 :url "http://localhost/"})
        id (:id node)
        merged (merge-node! conn id {:url "http://localhost/redirected/"})
        ]
    (is (= (:id node) (:id merged)))
    (is (= (get-in merged [:data :url]) "http://localhost/redirected/"))
    (are [f path] (f (get-in merged path) (get-in node path))
                  > [:data :timestamp]                      ; merge-node should update the timestamp
                  = [:data :nextupdate]                     ; merge-node should not touch the nextupdate
                  not= [:data :url]                         ; we changed the url
                  )
    ))

(deftest test-create-or-merge
  (wipe-test-db)
  ; First let's test creating from scratch
  (let [conn (get-test-connection)
        data-items {:id "TestNode/CoM" :label "TestNode" :url "http://changeme"}
        node (create-or-merge-node! conn data-items)]
    (is (not= node nil))
    (is (= (get-in node [:data :id]) "TestNode/CoM"))
    (is (= (get-in node [:data :url]) "http://changeme"))
    ; Test that we can call create-or-merge if one exists
    (let [merged (create-or-merge-node! conn (assoc data-items :url "http://newurl"))]
      (is (= (:id merged) (:id node)))
      (is (= (get-in merged [:data :url]) "http://newurl"))
      (is (= (get-in merged [:data :id]) "TestNode/CoM"))
      )
    ))

(deftest test-create-or-retrieve
  (wipe-test-db)
  ; First let's test creating from scratch
  (let [conn (get-test-connection)
        data-items {:id "TestNode/CoM" :label "TestNode" :url "http://original"}
        node (create-or-retrieve-node! conn data-items)]
    (is (not= node nil))
    (is (= (get-in node [:data :id]) "TestNode/CoM"))
    (is (= (get-in node [:data :url]) "http://original"))
    ; Test that we can if one exists, the new URL is ignored
    (let [merged (create-or-retrieve-node! conn (assoc data-items :url "http://newurl"))]
      (is (= (:id merged) (:id node)))
      (is (= (get-in merged [:data :url]) "http://original"))
      (is (= (get-in merged [:data :id]) "TestNode/CoM"))
      )
    ))

