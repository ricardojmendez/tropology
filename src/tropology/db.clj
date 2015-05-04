(ns tropology.db
  (:require [joda-time :as j]
            [clojure.string :refer [lower-case]]
            [korma.core :refer :all]
            [korma.db :as kdb]
            [tropology.base :as b]
            [taoensso.timbre.profiling :as prof]
            [environ.core :refer [env]]
            [com.numergent.url-tools :as u]))


;
; Database definition
;

(defn- key-rename [n]
  (clojure.string/replace n "-" "_"))

(defn- sql-field-rename [n]
  (-> n
      name
      (clojure.string/replace "_" "-")
      keyword)
  )

(defn rename-db-keywords [row]
  (->> (keys row)
       (map #(hash-map % (sql-field-rename %)))
       (into {})
       (clojure.set/rename-keys row)))


(kdb/defdb test-db (kdb/postgres {:host     (:db-host env)
                                  :db       (:db-name env)
                                  :user     (:db-user env)
                                  :password (:db-password env)
                                  :naming   {:keys   key-rename
                                             :fields key-rename}
                                  }))

(defentity pages)
(defentity links)


(defn save-page!
  "Saves a page record, or updates an existing one.

  Likely could be re-writen to use create-if-unknown! passing an else
  function to be executed in the case that the record exists."
  [record]
  (->>
    (let [code    (:code record)
          current (select pages (where {:code code}))]
      (if (empty? current)
        (insert pages (values record))
        (update pages (set-fields record) (where {:code code})))
      )
    (prof/p :save-page)))


;
; Timestamp functions
;

(def expiration-period (j/days (Integer. (:expiration env))))

(defn timestamp-next-update
  "Updates a data hashmap with the current time and the next time for update,
  in milliseconds.
  See http://joda-time.sourceforge.net/apidocs/org/joda/time/Instant.html"
  [data]
  (let [now (j/date-time)]
    (assoc data :time-stamp (.getMillis now)
                :next-update (.getMillis (j/plus now expiration-period)))))

(defn timestamp-update
  "Updates a data hashmap with the current time and the next time for update,
  in milliseconds.
  See http://joda-time.sourceforge.net/apidocs/org/joda/time/Instant.html"
  [data]
  (assoc data :time-stamp (.getMillis (j/date-time))))

(defn timestamp-create
  "Adds to a data hashmap the current time and the next time for update,
  in milliseconds.  If the hashmap already contains either of these values,
  they are preseved.
  See http://joda-time.sourceforge.net/apidocs/org/joda/time/Instant.html"
  [data]
  (let [now (.getMillis (j/date-time))]
    (merge {:time-stamp now :next-update now} data)))


;
; Query helpers
;

(defn code-to-match
  "Returns match pattern string for an id, including the node label, with
  the prefix name for the pattern.

  I'm not a fan of not being able to pass the label as a parameter, but them's
  the breaks. neo4j's parsing seems strong enough that code injection is
  unlikely."
  ([prefix]
   (code-to-match prefix "id" b/base-label))
  ([prefix id-param]
   (code-to-match prefix id-param b/base-label))
  ([prefix id-param label]
   (str "(" prefix ":" label " {code:{" id-param "}})")))


;
; Page import
;


(defn create-all!
  "Creates a list of pages"
  [records]
  (->>
    (if (not-empty records)
      (insert pages (values records)))                      ; Doesn't return all records created, just the last one
    (prof/p :create-all)))


(defn query-for-codes
  "Returns the pages for the list of codes provided"
  [codes]
  (->> (select pages
               (fields :code :display :category :url :has-error :error :is-redirect :incoming :outgoing :next-update :time-stamp)
               (where {:code [in (map lower-case codes)]}))
       (map rename-db-keywords)
       (prof/p :query-for-codes)
       ))


(defn create-all-unknown! [records]
  (->> (let [codes     (map :code records)
             existing  (->> (query-for-codes codes) (map :code))
             remaining (remove #(u/in-seq? existing (:code %)) records)
             ]
         (create-all! remaining))
       (prof/p :create-all-unknown))
  )

(defn create-relationships! [code links-to rel]
  (if (not-empty links-to)
    (do (->> (delete links (where {:from-code code :type (name rel)}))
             (prof/p :delete-links))
        (->> (insert links
                     (values (pmap #(hash-map :from-code code :to-code % :type (name rel)) links-to)))
             (prof/p :create-links))))
  )


(defn log-error!
  "Logs an error for a node and returns the updated node"
  [node]
  (let [code (lower-case (:code node))
        data (-> (merge {:display code :category (b/category-from-code code) :image nil :has-error false :is-redirect false} node) ; Add defaults
                 (merge {:code code})
                 timestamp-update)
        _    (save-page! data)]
    (->> (query-for-codes [code])
         first)))


(defn create-page-and-links!
  "Creates a page and all its related to links in a single call.

  Any pages it needs to create in order to link to _will not_ have the URL
  or category set, since I haven't found a way to pass a map yet.

  I considered doing a try-times here so that we can parallelize and retry
  in transient exceptions. It doesn't make any sense to retry and walk into
  the same deadlock right away, and pmap doesn't create a thread for each
  item... so putting a thread to sleep when one fails actually makes the
  whole process slower on my tests.

  http://stackoverflow.com/questions/1879885/clojure-how-to-to-recur-upon-exception
  http://stackoverflow.com/questions/5021788/how-many-threads-does-clojures-pmap-function-spawn-for-url-fetching-operations
  "
  ([node]
   (create-page-and-links! node nil nil {:is-redirect false}))
  ([node rel links {:keys [is-redirect redirector]}]
   (let [link-codes (map :code links)
         all-codes  (conj link-codes (:code node))]
     (kdb/transaction
       (save-page! (timestamp-next-update node))
       (create-all-unknown! links)
       (create-relationships! (:code node) link-codes rel)
       (update pages
               (where {:code [in all-codes]})
               (set-fields {:outgoing (raw "(SELECT COUNT(*) FROM links WHERE from_code = code)")
                            :incoming (raw "(SELECT COUNT(*) FROM links WHERE to_code = code)")
                            }))
       (if is-redirect
         (-> redirector
             timestamp-update
             (assoc :is-redirect true)
             save-page!))
       ))
    ))


;
; Node query and creation functions
;


(defn query-by-code
  "Queries for a node id on the properties. Does not filter by label. Notice
  that this is not the same as getting the node directly via its internal id."
  [code]
  (->>
    (->> (query-for-codes [code])
         first)
    (prof/p :query-by-code)))

(defn query-nodes-to-crawl
  "Return the nodes that need to be crawled according to their nextupdate timestamp"
  ([]
   (query-nodes-to-crawl 100))
  ([node-limit]
   (query-nodes-to-crawl node-limit (.getMillis (j/date-time))))
  ([node-limit time-limit]
   (if (> node-limit 0)                                     ; If we pass a limit of 0, applying ORDER BY will raise an exception
     (->> (select pages
                  (fields :url)
                  (where {:is-redirect false :has-error false :next-update [< time-limit]})
                  (order :next-update :ASC)
                  (limit node-limit))
          (map :url))
     '())))

;
; Link querying
;

(defn query-node-rel
  [^String code rel from to]
  (->> (select pages
               (fields :code :url :title :display :url :category :incoming :outgoing)
               (join links (= to :code))
               (where {from (lower-case code) :links.type (name rel)})
               )
       (map rename-db-keywords))
  )

(defn query-from
  "Retrieves the list of nodes that a node links to (emanating from)

  We could probably write it getting the relationships and walking through
  them, but going with cypher for now to test."
  [^String code rel]
  (query-node-rel code rel :links.from-code :links.to-code))

(defn query-to
  "Retrieves the list of nodes that links to a node.
  Yes, the parameter order is the opposite from query-links-from,
  since I think it better indicates the relationship."
  [rel ^String code]
  (query-node-rel code rel :links.to-code :links.from-code))

(defn query-common-nodes-from
  "Given a starting node code (common-code) and a list of node codes-from
  to query from, it returns the information for all nodes that code-from
  links out to that are also related to the starting node code in any
  direction."
  ([^String common-code codes-from]
   (query-common-nodes-from common-code codes-from :LINKSTO 1000))
  ([^String common-code codes-from rel incoming-link-limit]
   (let [code  (lower-case common-code)
         rels  (->>
                 (union
                   (queries (subselect links
                                       (fields [:from-code :code])
                                       (where {:to-code code :type (name rel)}))
                            (subselect links
                                       (fields [:to-code :code])
                                       (where {:from-code code :type (name rel)}))))
                 (map :code))
         final (if (not-empty codes-from)
                 (filter #(u/in-seq? (map lower-case codes-from) %) rels)
                 rels
                 )
         ]
     (->> (select links
                  (join pages (= :links.to-code :pages.code))
                  (where {:type           (name rel)
                          :from-code      [in final]
                          :to-code        [in rels]
                          :pages.incoming [< incoming-link-limit]
                          })
                  )
          (map rename-db-keywords)
          )
     )))
