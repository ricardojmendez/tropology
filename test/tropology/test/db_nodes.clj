(ns tropology.test.db-nodes
  (:require [clojure.test :refer :all]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [joda-time :as jt]
            [tropology.db :refer :all]
            [tropology.base :as b]))

(defn get-test-connection []
  (nr/connect "http://neo4j:testneo4j@192.168.59.103:7373/db/data/"))


(defn wipe-test-db []
  (let [conn (get-test-connection)]
    (cy/tquery conn "MATCH (n)-[r]-(m) DELETE n,r,m")       ; Delete notes with relationships
    (cy/tquery conn "MATCH (n) DELETE n")                   ; Delete singletons
    ))


(defn get-all-articles []
  (->> (cy/tquery (get-test-connection) "MATCH (n:Article) RETURN n")
       (map #(get % "n"))))

(defn get-all-article-rels []
  (cy/tquery (get-test-connection) "MATCH (n:Article)-[r]->(m:Article) RETURN n.code as from, type(r) as type, m.code as to"))



(deftest test-id-to-match
  (is (= (code-to-match "p") "(p:Article {code:{id}})"))
  (is (= (code-to-match "pref" "c") "(pref:Article {code:{c}})"))
  (is (= (code-to-match "pref" "c" "Label") "(pref:Label {code:{c}})")))



(deftest test-create-node
  (wipe-test-db)
  (let [node (create-node! (get-test-connection) "TestNode" {:code "TestNode/First" :nextUpdate 5 :url "http://localhost/"})]
    (is (not= node nil))
    (are [path result] (= (get-in node path) result)
                       [:data :code] "TestNode/First"
                       [:data :nextUpdate] 5
                       [:data :url] "http://localhost/")))


(deftest test-create-node-assigns-timestamps
  (wipe-test-db)
  (let [node (create-node! (get-test-connection) "TestNode" {:code "TestNode/First"})]
    (is (not= node nil))
    (are [path] (> (get-in node path) 0)
                [:data :timeStamp]
                [:data :nextUpdate])))


(deftest test-query-by-code
  (let [conn (get-test-connection)]
    (wipe-test-db)
    (is (nil? (query-by-code conn "TestNode/ForQuerying")))
    (let [created (create-node! conn b/base-label {:code "TestNode/ForQuerying"})
          node    (query-by-code conn "TestNode/ForQuerying")]
      (is (some? created))
      (is (not= node nil))
      (are [path result] (= (get-in node path) result)
                         [:metadata :labels] ["Article"]
                         [:data :code] "TestNode/ForQuerying"
                         ))))


(deftest test-query-nodes-when-empty
  (wipe-test-db)
  (is (= (count (query-nodes-to-crawl (get-test-connection) 100)) 0))
  (is (= (query-nodes-to-crawl (get-test-connection) 100) '())))


(defn create-test-nodes
  ([n isRedirect]
   (create-test-nodes n isRedirect 0))
  ([n isRedirect base-n]
   (dotimes [s n]
     (let [i (+ s base-n)]
       (create-node! (get-test-connection) b/base-label {:code       (str "TestNode/" i)
                                                         :nextUpdate i
                                                         :url        (str i) ; Keep the i as the url for ease of testing
                                                         :isRedirect isRedirect
                                                         :hasError   false})))))

(deftest test-query-nodes-node-limit
  (wipe-test-db)
  (create-test-nodes 30 false)                              ; Create non-redirect nodes
  (is (= 30 (count (query-nodes-to-crawl (get-test-connection) 100))))
  (is (= 15 (count (query-nodes-to-crawl (get-test-connection) 15))))
  (is (= (query-nodes-to-crawl (get-test-connection) 0) '())))


(deftest test-query-nodes-node-skip-errors
  (wipe-test-db)
  (create-test-nodes 30 false)
  (create-or-merge-node! (get-test-connection) {:code "TestNode/10" :category "TestNode" :hasError true :error "Oopsy"})
  (is (= (count (query-nodes-to-crawl (get-test-connection) 100)) 29)) ; We skip the error node
  (is (= (count (query-nodes-to-crawl (get-test-connection) 15)) 15)) ; We can still find 15 nodes to crawl
  )


(deftest test-query-nodes-time-limit
  (wipe-test-db)
  (create-test-nodes 15 false)
  (create-test-nodes 3 true 20)
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
        n1   (create-node! conn b/base-label {:code "TestNode/N1"})
        n2   (create-node! conn b/base-label {:code "TestNode/N2"})
        rel  (relate-nodes! conn :LINKSTO n1 n2)]
    (is (not= rel nil))
    (are [query result] (= query result)
                        (:type rel) "LINKSTO"
                        (:start rel) (:location-uri n1)
                        (:end rel) (:location-uri n2))
    ))


(deftest test-query-from
  (let [conn (get-test-connection)
        n1   (create-node! conn b/base-label {:code "TestNode/N1"})
        n2   (create-node! conn b/base-label {:code "TestNode/N2"})
        n3   (create-node! conn b/base-label {:code "TestNode/N3"})
        _    (relate-nodes! conn :LINKSTO n1 n2)
        _    (relate-nodes! conn :LINKSTO n1 n3)
        _    (relate-nodes! conn :LINKSTO n2 n3)
        ]
    (let [r (query-from conn "TestNode/N1" :LINKSTO)]
      (is (= (count r) 2))
      (is (some #(= (% "code") "TestNode/N2") r))
      (is (some #(= (% "code") "TestNode/N3") r)))
    (let [r (query-from conn "TestNode/N2" :LINKSTO)]
      (is (= (count r) 1))
      (is (some #(= (% "code") "TestNode/N3") r)))
    (let [r (query-from conn "TestNode/N3" :LINKSTO)]
      (is (empty? r)))
    ))

(deftest test-query-to
  (let [conn (get-test-connection)
        n1   (create-node! conn b/base-label {:code "TestNode/N1"})
        n2   (create-node! conn b/base-label {:code "TestNode/N2"})
        n3   (create-node! conn b/base-label {:code "TestNode/N3"})
        _    (relate-nodes! conn :LINKSTO n1 n2)
        _    (relate-nodes! conn :LINKSTO n1 n3)
        _    (relate-nodes! conn :LINKSTO n2 n3)
        ]
    (let [r (query-to conn :LINKSTO "TestNode/N3")]
      (is (= (count r) 2))
      (is (some #(= (% "code") "TestNode/N1") r))
      (is (some #(= (% "code") "TestNode/N2") r)))
    (let [r (query-to conn :LINKSTO "TestNode/N2")]
      (is (= (count r) 1))
      (is (some #(= (% "code") "TestNode/N1") r)))
    (let [r (query-to conn :LINKSTO "TestNode/N1")]
      (is (empty? r)))
    ))

(deftest test-common-from
  (let [conn (get-test-connection)
        n1   (create-node! conn b/base-label {:code "TestNode/N1" :incoming 100})
        n2   (create-node! conn b/base-label {:code "TestNode/N2" :incoming 500}) ; To test limits later
        n3   (create-node! conn b/base-label {:code "TestNode/N3" :incoming 100})
        n4   (create-node! conn b/base-label {:code "TestNode/N4" :incoming 100})
        n5   (create-node! conn b/base-label {:code "TestNode/N5" :incoming 100})
        n6   (create-node! conn b/base-label {:code "TestNode/N7" :incoming 100})
        _    (relate-nodes! conn :LINKSTO n1 n2)
        _    (relate-nodes! conn :LINKSTO n1 n3)
        _    (relate-nodes! conn :LINKSTO n1 n5)
        _    (relate-nodes! conn :LINKSTO n1 n6)
        _    (relate-nodes! conn :LINKSTO n2 n3)
        _    (relate-nodes! conn :LINKSTO n2 n4)
        _    (relate-nodes! conn :LINKSTO n3 n4)
        _    (relate-nodes! conn :LINKSTO n3 n5)
        _    (relate-nodes! conn :LINKSTO n4 n5)
        _    (relate-nodes! conn :LINKSTO n4 n6)
        _    (relate-nodes! conn :DIFFREL n1 n4)            ; To be excluded in most tests below
        _    (relate-nodes! conn :DIFFREL n1 n3)            ; To be excluded in most tests below
        _    (relate-nodes! conn :DIFFREL n3 n4)            ; To be excluded in most tests below
        ]
    ; Test relationships
    (let [r (query-common-nodes-from conn "TestNode/N3" "TestNode/N1")]
      (is (= 2 (count r)))                                  ; N1 links out to N2 and N5, and both are related to N3
      (is (some #(= (% "code") "TestNode/N2") r))
      (is (some #(= (% "code") "TestNode/N5") r)))
    (let [r (query-common-nodes-from conn "TestNode/N3" "TestNode/N2")]
      (is (= 1 (count r)))
      (is (some #(= (% "code") "TestNode/N4") r)))          ; N2 links out to N4. N1 is excluded since N2 doesn't link out to it
    (let [r (query-common-nodes-from conn "TestNode/N3" "TestNode/N4")]
      (is (= 1 (count r)))
      (is (some #(= (% "code") "TestNode/N5") r)))          ; N4 links out to N5, which is related to N3. N6 is excluded because it' not related.
    (let [r (query-common-nodes-from conn "TestNode/N3" "TestNode/N5")]
      (is (empty? r)))
    (let [r (query-common-nodes-from conn "TestNode/N1" "TestNode/N2")]
      (is (= 2 (count r)))
      (is (some #(= (% "code") "TestNode/N3") r))           ; N2 links out to N3 and N4, which are related to N1.
      (is (some #(= (% "code") "TestNode/N4") r)))          ; Other N1 relationships like N5 and N6 are ignored because N2 doesn't link out to them.
    ; Test incoming limits
    (let [r (query-common-nodes-from conn "TestNode/N3" "TestNode/N1" :LINKSTO 400)]
      (is (= 1 (count r)))                                  ; N2 is excluded because of too many incoming links
      (is (some #(= (% "code") "TestNode/N5") r)))
    ; Test link relationship types
    (let [r (query-common-nodes-from conn "TestNode/N3" "TestNode/N1" :DIFFREL 1000)]
      (is (= 1 (count r)))                                  ; Only one node in common with that relationship type
      (is (some #(= (% "code") "TestNode/N4") r)))

    ))




(deftest test-update-link-count
  (wipe-test-db)
  (let [conn     (get-test-connection)
        n1       (create-node! conn b/base-label {:code "TestNode/N1"})
        n2       (create-node! conn b/base-label {:code "TestNode/N2"})
        n3       (create-node! conn b/base-label {:code "TestNode/N3"})
        n4       (create-node! conn b/base-label {:code "TestNode/N4"})
        _        (relate-nodes! conn :LINKSTO n1 n2)
        _        (relate-nodes! conn :LINKSTO n1 n3)
        _        (relate-nodes! conn :LINKSTO n2 n3)
        _        (relate-nodes! conn :LINKSTO n3 n4)
        _        (relate-nodes! conn :IGNORED n1 n2)        ; Won't be counted
        _        (relate-nodes! conn :IGNORED n2 n3)        ; Won't be counted
        original (cy/tquery conn "MATCH (n:Article) RETURN n.code, n.incoming, n.outgoing ")
        _        (update-link-count! conn)                  ; Does not actually return a value, we just care about executing it
        updated  (cy/tquery conn "MATCH (n:Article) RETURN n.code, n.incoming, n.outgoing ")
        ]
    (is (every? #(= nil (% "n.incoming") (% "n.outgoing")) original)) ; Original list does not have any incoming or outgoing values
    (are [id key count] (= ((first (filter #(= (% "n.code") id) updated)) key) count)
                        "TestNode/N1" "n.incoming" 0
                        "TestNode/N1" "n.outgoing" 2
                        "TestNode/N2" "n.incoming" 1
                        "TestNode/N2" "n.outgoing" 1
                        "TestNode/N3" "n.incoming" 2
                        "TestNode/N3" "n.outgoing" 1
                        "TestNode/N4" "n.incoming" 1
                        "TestNode/N4" "n.outgoing" 0
                        )
    ))




(deftest test-merge-node
  ; No need to wipe the db
  (let [conn   (get-test-connection)
        node   (create-node! conn b/base-label {:code "TestNode/First" :nextUpdate 5 :url "http://localhost/"})
        id     (:id node)
        merged (merge-node! conn id {:url "http://localhost/redirected/"})
        ]
    (is (= (:code node) (:code merged)))
    (is (= (get-in merged [:data :url]) "http://localhost/redirected/"))
    (are [f path] (f (get-in merged path) (get-in node path))
                  > [:data :timeStamp]                      ; merge-node should update the timestamp
                  = [:data :nextUpdate]                     ; merge-node should not touch the nextupdate
                  not= [:data :url]                         ; we changed the url
                  )
    ))

(deftest test-create-or-merge
  (wipe-test-db)
  ; First let's test creating from scratch
  (let [conn       (get-test-connection)
        data-items {:code "TestNode/CoM" :category "TestNode" :url "http://changeme"}
        node       (create-or-merge-node! conn data-items)]
    (is (not= node nil))
    (is (= (get-in node [:data :code]) "TestNode/CoM"))
    (is (= (get-in node [:data :url]) "http://changeme"))
    ; Test that we can call create-or-merge if one exists
    (let [merged (create-or-merge-node! conn (assoc data-items :url "http://newurl"))]
      (is (= (:code merged) (:code node)))
      (is (= (get-in merged [:data :url]) "http://newurl"))
      (is (= (get-in merged [:data :code]) "TestNode/CoM"))
      )
    ))

(deftest test-create-or-retrieve
  (wipe-test-db)
  ; First let's test creating from scratch
  (let [conn       (get-test-connection)
        data-items {:code "TestNode/CoM" :category "TestNode" :url "http://original"}
        node       (create-or-retrieve-node! conn data-items)]
    (is (not= node nil))
    (is (= (get-in node [:data :code]) "TestNode/CoM"))
    (is (= (get-in node [:data :url]) "http://original"))
    ; Test that we can if one exists, the new URL is ignored
    (let [merged (create-or-retrieve-node! conn (assoc data-items :url "http://newurl"))]
      (is (= (:code merged) (:code node)))
      (is (= (get-in merged [:data :url]) "http://newurl"))
      (is (= (get-in merged [:data :code]) "TestNode/CoM"))
      )
    ))
