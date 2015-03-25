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


(deftest test-relate-nodes
  ; We don' wipe the db to ensure association works even if there were previous nodes
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

(deftest test-merge-node
  ; No need to wipe the db
  (let [conn (get-test-connection)
        node (create-node! conn "TestNode" {:id "TestNode/First" :nextupdate 5 :url "http://localhost/"})
        id (:id node)
        merged (merge-node conn id {:url "http://localhost/redirected/"})
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

