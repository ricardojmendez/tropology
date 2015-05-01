(ns tropology.db
  (:require [joda-time :as j]
            [clojure.string :refer [lower-case]]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest.labels :as nl]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [clojurewerkz.neocons.rest.relationships :as nrl]
            [clojurewerkz.neocons.rest.transaction :as tx]
            [tropology.base :as b]
            [com.numergent.url-tools :as ut]
            [taoensso.timbre.profiling :as prof]
            [environ.core :refer [env]]))


(defn get-connection
  "Trivial. Returns a local connection."
  []
  (nr/connect (:db-url env)))


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
    (assoc data :timeStamp (.getMillis now)
                :nextUpdate (.getMillis (j/plus now expiration-period)))))

(defn timestamp-update
  "Updates a data hashmap with the current time and the next time for update,
  in milliseconds.
  See http://joda-time.sourceforge.net/apidocs/org/joda/time/Instant.html"
  [data]
  (assoc data :timeStamp (.getMillis (j/date-time))))

(defn timestamp-create
  "Adds to a data hashmap the current time and the next time for update,
  in milliseconds.  If the hashmap already contains either of these values,
  they are preseved.
  See http://joda-time.sourceforge.net/apidocs/org/joda/time/Instant.html"
  [data]
  (let [now (.getMillis (j/date-time))]
    (merge {:timeStamp now :nextUpdate now} data)))


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
  ([conn node]
   (create-page-and-links! conn node nil nil {:isRedirect false}))
  ([conn node rel links {:keys [isRedirect redirector]}]
   (let [code    (lower-case (:code node))
         all     (conj links code)
         main-st (str "MERGE (p:Article {code:{maincode}}) SET "
                      " p.url = {url}, p.category = {category}, p.host = {host}, "
                      " p.title = {title}, p.image = {image}, p.type = {type}, "
                      " p.nextUpdate = {nextUpdate}, p.timeStamp = {timeStamp}, "
                      " p.display = {display}, "
                      " p.hasError = {hasError}, p.isRedirect = {isRedirect} "
                      (if (some? links)
                        (str "FOREACH (link in {links} |"
                             " MERGE (p2:Article {code:link}) "
                             " ON CREATE SET p2.nextUpdate = {timeStamp}, p2.hasError = false, p2.isRedirect = false, p2.timeStamp = {timeStamp} "
                             " CREATE UNIQUE (p)-[" rel "]->(p2))"))
                      )
         p       (-> (merge {:host b/base-host :category (b/category-from-code code) :image nil :hasError false :isRedirect false} node) ; Add defaults
                     timestamp-next-update
                     (assoc :maincode code :links links))]
     (tx/in-transaction
       conn
       (tx/statement main-st p)
       (tx/statement "MATCH ()-[r:LINKSTO]->(n:Article) WHERE n.code in {links} WITH n, COUNT(r) as incoming SET n.incoming = incoming" {:links all})
       (tx/statement (str "MATCH " (code-to-match "n") "-[r:LINKSTO]->() WITH n, COUNT(r) as outgoing SET n.outgoing = outgoing") {:id code})
       (tx/statement "MATCH (n:Article) WHERE n.outgoing is null AND n.code in {links} WITH n SET n.outgoing = 0" {:links all})
       (tx/statement "MATCH (n:Article) WHERE n.incoming is null AND n.code in {links} WITH n SET n.incoming = 0" {:links all})
       (if isRedirect
         (tx/statement "MERGE (p:Article {code:{code}}) SET p.isRedirect = true, p.nextUpdate = 0, p.timeStamp = {timeStamp}, p.hasError = false"
                       {:code redirector, :timeStamp (:timeStamp node)})))
     ))
  )

(defn log-error!
  "Logs an error for a node"
  [conn node]
  (let [code    (lower-case (:code node))
        main-st (str "MERGE (p:Article {code:{code}}) "
                     " SET "
                     " p.url = {url}, p.hasError = {hasError}, p.error = {error}, p.category = {category} "
                     "RETURN p")
        p       (-> (merge {:host b/base-host :category (b/category-from-code code) :image nil :hasError false :isRedirect false} node) ; Add defaults
                    (merge {:code code})
                    timestamp-next-update)]
    (->> (tx/in-transaction conn (tx/statement main-st p))
         first
         :data
         first
         :row
         first
         )))


;
; Node update functions
;

(defn update-link-count!
  "Updates the incoming and outgoing link count for all nodes in the database"
  [conn]
  (->>
    (tx/in-transaction
      conn
      (tx/statement "MATCH ()-[r:LINKSTO]->(n:Article) WITH n, COUNT(r) as incoming SET n.incoming = incoming")
      (tx/statement "MATCH (n:Article)-[r:LINKSTO]->() WITH n, COUNT(r) as outgoing SET n.outgoing = outgoing")
      (tx/statement "MATCH (n:Article) WHERE n.outgoing is null WITH n SET n.outgoing = 0")
      (tx/statement "MATCH (n:Article) WHERE n.incoming is null WITH n SET n.incoming = 0"))
    (prof/p :update-link-count))
  )

;
; Node query and creation functions
;


(defn query-for-codes
  "Queries for articles with a code from a list"
  [conn codes]
  (->>
    (tx/in-transaction
      conn
      (tx/statement "MATCH (n:Article) WHERE n.code in {list} RETURN n" {:list (map lower-case codes)}))
    first
    :data
    (map #(first (:row %)))
    (prof/p :query-for-codes)
    ))

(defn query-by-code
  "Queries for a node id on the properties. Does not filter by label. Notice
  that this is not the same as getting the node directly via its internal id."
  [conn code]
  (->>
    (let [query-str (str "MATCH  " (code-to-match "v") " RETURN v")
          match     (first (cy/tquery conn query-str {:id (lower-case code)}))]
      (if (nil? match)
        nil
        (-> (match "v")
            (select-keys [:data :metadata]))))
    (prof/p :query-by-code)))

(defn query-nodes-to-crawl
  "Return the nodes that need to be crawled according to their nextupdate timestamp"
  ([conn]
   (query-nodes-to-crawl conn 100))
  ([conn node-limit]
   (query-nodes-to-crawl conn node-limit (.getMillis (j/date-time))))
  ([conn node-limit time-limit]
   (if (> node-limit 0)                                     ; If we pass a limit of 0, applying ORDER BY will raise an exception
     (->> (cy/tquery conn "MATCH (v:Article) WHERE not v.isRedirect AND not v.hasError AND v.nextUpdate < {now} RETURN v.code as code, v.url as url ORDER BY v.nextUpdate LIMIT {limit}" {:now time-limit :limit node-limit})
          (map #(ut/if-empty (% "url") (str b/base-url (% "code")))))
     '())))

;
; Link querying
;

(defn query-from
  "Retrieves the list of nodes that a node links to (emanating from)

  We could probably write it getting the relationships and walking through
  them, but going with cypher for now to test."
  [conn ^String code rel]
  (let [query-str (str "MATCH " (code-to-match "o" "id") "-[" rel "]->(v:Article) RETURN DISTINCT v.code as code,v.display as display,v.url as url, v.title as title, v.label as label, v.incoming as incoming")]
    (cy/tquery conn query-str {:id (lower-case code)})))

(defn query-to
  "Retrieves the list of nodes that links to a node.
  Yes, the parameter order is the opposite from query-links-from,
  since I think it better indicates the relationship."
  [conn rel ^String code]
  (let [query-str (str "MATCH " (code-to-match "o" "id") "<-[" rel "]-(v) RETURN DISTINCT v.code as code,v.display as display,v.url as url, v.title as title, v.label as label, v.incoming as incoming")]
    (cy/tquery conn query-str {:id (lower-case code)})))

(defn query-common-nodes-from
  "Given a starting node code (common-code) and a list of node codes-from
  to query from, it returns the information for all nodes that code-from
  links out to that are also related to the starting node code in any
  direction."
  ([conn ^String common-code codes-from]
   (query-common-nodes-from conn common-code codes-from :LINKSTO 1000))
  ([conn ^String common-code codes-from rel incoming-link-limit]
    ; PROFILE MATCH (n:Article {code:"Anime/CowboyBebop"})--(m:Article {code:"Main/NoHoldsBarredBeatdown"})-[r:LINKSTO]->(o:Article)--(n) WHERE o.incoming < 1000 RETURN DISTINCT o;
   (->>
     (tx/in-transaction
       conn
       (tx/statement (str "MATCH "
                          (code-to-match "n" "idn")
                          "--(m:Article)-[" rel "]->(o:Article)--(n) "
                          "WHERE m.code in {codes} and (o.incoming is null or o.incoming < {limit}) "
                          "RETURN DISTINCT m.code as from, o.code as to")
                     {:idn (lower-case common-code) :codes (map lower-case codes-from) :limit incoming-link-limit}))

     first
     :data
     (map #(hash-map :from (first (:row %)) :to (second (:row %))))
     (prof/p :query-common-nodes-from)
     )
    ))
