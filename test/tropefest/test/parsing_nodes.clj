(ns tropefest.test.parsing-nodes
  (:require [clojure.test :refer :all]
            [tropefest.test.db-nodes :as tdb]
            [tropefest.test.parsing :as tp]
            [tropefest.parsing :refer :all]
            [tropefest.db :as db]))


;
; Exception logging
;


(deftest test-log-exception
  (tdb/wipe-test-db)
  (let [conn          (tdb/get-test-connection)
        url           "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop"
        original-data (-> (node-data-from-url url) (assoc :isredirect true)) ; Create a redirect node
        original-node (db/create-node! conn "Anime" original-data)
        t             (Throwable. "Oopsy")
        logged-node   (log-node-exception! conn url t)]
    (is (not= logged-node nil))
    (is (get-in original-node [:data :isredirect]))                        ; We did create a redirect node
    (is (get-in logged-node [:data :isredirect]))                 ; Redirect value did not change
    (is (nil? (get-in original-node [:data :error])))
    (is (= (get-in logged-node [:data :error]) "Oopsy"))))



;
; Parsing
;

(deftest test-save-page-links-local
  ; Slow test, saving 732 nodes ain't cheap
  (tdb/wipe-test-db)
  (let [name   (str tp/test-file-path "CowboyBebop.html")
        conn   (tdb/get-test-connection)
        loaded (load-resource-url name)
        saved  (save-page-links! conn loaded)]
    ; (println saved)
    (is (= (count saved) 732))
    ))

