(ns tropefest.test.parsing-nodes
  (:require [clojure.test :refer :all]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [taoensso.timbre.profiling :as prof]
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
        original-node (db/create-node! conn "Article" original-data)
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
; Parsing
;


(deftest test-record-page-local
  ; Slow test, saving 732 nodes ain't cheap
  ;
  ; Note that we load the page locally, so that we can pass a file that we
  ; know how may URLs it's supposed to include, while adding a provenance
  ; URL so that parsing and tagging takes place as expected.
  (tdb/wipe-test-db)
  (->>
    (let [path  (str tp/test-file-path "CowboyBebop.html")
          conn  (tdb/get-test-connection)
          _     (record-page! conn path "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop")
          saved (tdb/get-all-articles)]
      (is (= 653 (count saved)))
      )
    (prof/profile :trace :Database)))

(deftest test-record-page-live
  ; Load a live page. I don't check for how many nodes we're saving since that can
  ; and will change along with the page.
  (tdb/wipe-test-db)
  (let [conn  (tdb/get-test-connection)
        _     (record-page! conn "http://tvtropes.org/pmwiki/pmwiki.php/Main/HomePage")
        saved (tdb/get-all-articles)
        node  (db/query-by-code conn "Main/HomePage")]
    (is (< 0 (count saved)))
    (are [property value] (= value (get-in node [:data property]))
                          :hasError false
                          :category "Main"
                          :code "Main/HomePage"
                          :isRedirect false
                          :url "http://tvtropes.org/pmwiki/pmwiki.php/Main/HomePage")
    ))

(deftest test-record-page-same-provenance
  ; See note on test-record-page-local about the provenance URL
  (tdb/wipe-test-db)
  (->>
    (let [path    (str tp/test-file-path "TakeMeInstead-pruned.html")
          conn    (tdb/get-test-connection)
          _       (record-page! conn path "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInstead")
          saved   (tdb/get-all-articles)
          node    (db/query-by-code conn "Main/TakeMeInstead")
          ignored (db/query-by-code conn "External/LinkOutsideWikiText")
          ]
      (is (= (count saved) 6))
      (is (nil? ignored))
      (are [property value] (= value (get-in node [:data property]))
                            :hasError false
                            :category "Main"
                            :code "Main/TakeMeInstead"
                            :isRedirect false
                            :url "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInstead")
      )
    (prof/profile :trace :Database)
    ))

(deftest test-record-page-twice
  ; See note on test-record-page-local about the provenance URL
  ;
  ; On this test I query for the links directly using cypher instead of our
  ; helper functions since those already do DISTINCT and would not return
  ; duplicates.
  (tdb/wipe-test-db)
  (let [path  (str tp/test-file-path "TakeMeInstead-pruned.html")
        conn  (tdb/get-test-connection)
        _     (record-page! conn path "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInstead")
        saved (tdb/get-all-articles)
        _     (record-page! conn path "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInstead")
        again (tdb/get-all-articles)
        links (cy/tquery conn "MATCH (n:Article {code:'Main/TakeMeInstead'})-[r]->() RETURN r")
        ]
    (is (= (count saved) 6))                                ; Five links, plus one satellite
    (is (= (count again) 6))                                ; Same number of nodes is returned the second time
    (is (= (count links) 5))                                ; No duplicated links are created
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
        _           (record-page! conn path "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInsteadRedirector")
        main-node   (db/query-by-code conn "Main/TakeMeInstead")
        redir-node  (db/query-by-code conn "Main/TakeMeInsteadRedirector")
        links-main  (db/query-from conn "Main/TakeMeInstead" :LINKSTO)
        links-redir (db/query-from conn "Main/TakeMeInsteadRedirector" :LINKSTO)]
    (are [property value] (= value (get-in main-node [:data property]))
                          :hasError false
                          :category "Main"
                          :code "Main/TakeMeInstead"
                          :isRedirect false
                          :url "http://tvtropes.org/pmwiki/pmwiki.php/Main/TakeMeInstead")
    (are [property value] (= value (get-in redir-node [:data property]))
                          :hasError false
                          ; :category "Main"
                          :code "Main/TakeMeInsteadRedirector"
                          :isRedirect true
                          ; Nodes that are initially created as redirects don't get a url or category
                          :category nil
                          :url nil
                          )
    (is (empty? links-redir))
    (is (= 5 (count links-main)))
    ))

