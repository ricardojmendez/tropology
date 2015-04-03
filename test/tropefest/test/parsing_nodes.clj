(ns tropefest.test.parsing-nodes
  (:require [clojure.test :refer :all]
            [tropefest.test.db-nodes :as tdb]
            [tropefest.test.parsing :as tp]
            [tropefest.parsing :refer :all]
            [tropefest.db :as db]
            [tropefest.parsing :as p]))


;
; Exception logging
;


(deftest test-log-exception
  (tdb/wipe-test-db)
  (let [conn          (tdb/get-test-connection)
        url           "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop"
        original-data (-> (node-data-from-url url) (assoc :isRedirect true)) ; Create a redirect node
        original-node (db/create-node! conn "Anime" original-data)
        t             (Throwable. "Oopsy")
        logged-node   (log-node-exception! conn url t)]
    (println "Yes, we were supposed to get an exception logged up there. ⤴")
    (is (not= logged-node nil))
    (is (get-in original-node [:data :isRedirect]))         ; We did create a redirect node
    (is (get-in logged-node [:data :isRedirect]))           ; Redirect value did not change
    (is (nil? (get-in original-node [:data :error])))
    (is (get-in logged-node [:data :hasError]))
    (is (= (get-in logged-node [:data :error]) "Oopsy"))))


;
; Handling a possible redirect case
;

(deftest test-mark-url-redirect
  ; First, we test the cases were we do tell it it's a redirect
  (tdb/wipe-test-db)
  (let [conn (tdb/get-test-connection)
        url  "http://tvtropes.org/pmwiki/pmwiki.php/Main/Redirector"
        _    (mark-url-redirect conn url)
        node (db/query-by-id conn "Main/Redirector")]
    (are [property value] (= value (get-in node [:data property]))
                          :isRedirect true
                          :id "Main/Redirector"
                          :url url))
  ; Finally, let's make sure that if the node exists, the :isRedirect property is set
  (tdb/wipe-test-db)
  (let [conn (tdb/get-test-connection)
        url  "http://tvtropes.org/pmwiki/pmwiki.php/Main/Redirector"
        meta (p/node-data-from-url url)
        orig (db/create-or-merge-node! conn meta)
        _    (mark-url-redirect conn url)
        node (db/query-by-id conn "Main/Redirector")]
    (is (= false (get-in orig [:data :isRedirect])))
    (is (= true (get-in node [:data :isRedirect])))
    (is (= (get-in node [:meta :id]) (get-in orig [:meta :id])))
    )
  )


;
; Parsing
;


(deftest test-record-page-local
  ; Slow test, saving 732 nodes ain't cheap
  ;
  ; Note that we load the page locally, so that we can pass a file that we
  ; know how may URLs it's supposed to include, while adding a provenance
  ; URL so that parsing and tagging takes place as expected.
  (tdb/wipe-test-db)
  (let [path  (str tp/test-file-path "CowboyBebop.html")
        conn  (tdb/get-test-connection)
        saved (record-page! conn path "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop")]
    (is (= (count saved) 732))
    ))

(deftest test-record-page-live
  ; Load a live page. I don't check for how many nodes we're saving since that can
  ; and will change along with the page.
  (tdb/wipe-test-db)
  (let [conn  (tdb/get-test-connection)
        saved (record-page! conn "http://tvtropes.org/pmwiki/pmwiki.php/Main/HomePage")
        node  (db/query-by-id conn "Main/HomePage")]
    (is (< 0 (count saved)))
    (are [property value] (= value (get-in node [:data property]))
                          :hasError false
                          :label "Main"
                          :id "Main/HomePage"
                          :isRedirect false
                          :url "http://tvtropes.org/pmwiki/pmwiki.php/Main/HomePage")
    ))

(deftest test-record-page-same-provenance
  ; See note on test-record-page-local about the provenance URL
  (tdb/wipe-test-db)
  (let [path  (str tp/test-file-path "TakeMeInstead-pruned.html")
        conn  (tdb/get-test-connection)
        saved (record-page! conn path "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInstead")
        node  (db/query-by-id conn "Main/TakeMeInstead")]
    (is (= (count saved) 5))
    (are [property value] (= value (get-in node [:data property]))
                          :hasError false
                          :label "Main"
                          :id "Main/TakeMeInstead"
                          :isRedirect false
                          :url "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInstead")
    ))

(deftest test-record-page-with-redirect
  ; On this test we pass a provenance URL which will differ from the page's og:url
  ; so that we can test that:
  ; - A node is created for the target page that we got redirected to and obtained the data from
  ; - All links are created from the id we got redirected to
  ; - There is a node created for the original URL and tagged as being a redirect
  ; - There are no links created from the original redirector
  (tdb/wipe-test-db)
  (let [path        (str tp/test-file-path "TakeMeInstead-pruned.html")
        conn        (tdb/get-test-connection)
        saved       (record-page! conn path "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInsteadRedirector")
        main-node   (db/query-by-id conn "Main/TakeMeInstead")
        redir-node  (db/query-by-id conn "Main/TakeMeInsteadRedirector")
        links-main  (db/query-from conn "Main/TakeMeInstead" :LINKSTO)
        links-redir (db/query-from conn "Main/TakeMeInsteadRedirector" :LINKSTO)]
    (is (= (count saved) 5))
    (are [property value] (= value (get-in main-node [:data property]))
                          :hasError false
                          :label "Main"
                          :id "Main/TakeMeInstead"
                          :isRedirect false
                          :url "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInstead")
    (are [property value] (= value (get-in redir-node [:data property]))
                          :hasError false
                          :label "Main"
                          :id "Main/TakeMeInsteadRedirector"
                          :isRedirect true
                          :url "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInsteadRedirector")
    (is (empty? links-redir))
    (is (= 5 (count links-main)))
    ))

