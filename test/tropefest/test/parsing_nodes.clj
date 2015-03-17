(ns tropefest.test.parsing-nodes
  (:require [clojure.test :refer :all]
            [tropefest.test.db-nodes :as tdb]
            [tropefest.test.parsing :as tp]
            [tropefest.parsing :refer :all]))


(deftest test-save-page-links-local
  ; Slow test, saving 732 nodes ain't cheap
  (tdb/wipe-test-db)
  (let [name      (str tp/test-file-path "CowboyBebop.html")
        conn      (tdb/get-test-connection)
        loaded    (load-resource-url name)
        saved     (save-page-links conn loaded)]
    ; (println saved)
    (is (= (count saved) 732))
    ))
