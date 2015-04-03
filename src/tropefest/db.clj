(ns tropefest.db
  (:require [joda-time :as j]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest.labels :as nl]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [clojurewerkz.neocons.rest.relationships :as nrl]
            [clojurewerkz.neocons.rest.index :as nri]
            [tropefest.base :as b]
            [taoensso.timbre.profiling :as p]
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
    (assoc data :timestamp (.getMillis now)
                :nextupdate (.getMillis (j/plus now expiration-period)))))

(defn timestamp-update
  "Updates a data hashmap with the current time and the next time for update,
  in milliseconds.
  See http://joda-time.sourceforge.net/apidocs/org/joda/time/Instant.html"
  [data]
  (assoc data :timestamp (.getMillis (j/date-time))))

(defn timestamp-create
  "Adds to a data hashmap the current time and the next time for update,
  in milliseconds.  If the hashmap already contains either of these values,
  they are preseved.
  See http://joda-time.sourceforge.net/apidocs/org/joda/time/Instant.html"
  [data]
  (let [now (.getMillis (j/date-time))]
    (merge {:timestamp now :nextupdate now} data)))

;
; Node update functions
;

(defn update-link-count!
  "Updates the incoming and outgoing link count for all nodes in the database"
  [conn]
  (do
    (cy/query conn "MATCH ()-[r:LINKSTO]->(n) WITH n, COUNT(r) as incoming SET n.incoming = incoming")
    (cy/query conn "MATCH (n)-[r:LINKSTO]->() WITH n, COUNT(r) as outgoing SET n.outgoing = outgoing")
    (cy/query conn "MATCH (n) WHERE n.outgoing is null WITH n SET n.outgoing = 0")
    (cy/query conn "MATCH (n) WHERE n.incoming is null WITH n SET n.incoming = 0")
    nil))

;
; Node query and creation functions
;

(defn id-to-match
  "Returns match pattern string for an id, including the node label, with
  the prefix name for the pattern.

  I'm not a fan of not being able to pass the label as a parameter, but them's
  the breaks. neo4j's parsing seems strong enough that code injection is
  unlikely."
  ([prefix id]
   (id-to-match prefix id "id")
    )
  ([prefix id id-param]
   (let [label (b/label-from-id id)]
     (str "(" prefix ":" label " {id:{" id-param "}})"))))

(defn query-by-id
  "Queries for a node id on the properties. Does not filter by label. Notice
  that this is not the same as getting the node directly via its internal id."
  [conn id]
  (->>
    (let [query-str (str "MATCH  " (id-to-match "v" id) " RETURN v")
          match     (first (cy/tquery conn query-str {:id id}))]
      (if (nil? match)
        nil
        (-> (match "v")
            (select-keys [:data :metadata]))))
    (p/p :query-by-id)))

(defn query-nodes-to-crawl
  "Return the nodes that need to be crawled according to their nextupdate timestamp"
  ([conn]
   (query-nodes-to-crawl conn 100))
  ([conn node-limit]
   (query-nodes-to-crawl conn node-limit (.getMillis (j/date-time))))
  ([conn node-limit time-limit]
   (if (> node-limit 0)                                     ; If we pass a limit of 0, applying ORDER BY will raise an exception
     (->> (cy/tquery conn "MATCH (v) WHERE not v.isRedirect AND not v.hasError AND v.nextupdate < {now} RETURN v.url ORDER BY v.nextupdate LIMIT {limit}" {:now time-limit :limit node-limit})
          (map #(% "v.url")))
     '())))

;
; Link querying
;

(defn query-from
  "Retrieves the list of nodes that a node links to (emanating from)

  We could probably write it getting the relationships and walking through
  them, but going with cypher for now to test."
  [conn ^String code rel]
  (let [query-str (str "MATCH " (id-to-match "o" code "id") "-[" rel "]->(v) RETURN DISTINCT v.id as id,v.url as url, v.title as title, v.label as label, v.incoming as incoming")]
    (cy/tquery conn query-str {:id code})))

(defn query-to
  "Retrieves the list of nodes that links to a node.
  Yes, the parameter order is the opposite from query-links-from,
  since I think it better indicates the relationship."
  [conn rel ^String code]
  (let [query-str (str "MATCH " (id-to-match "o" code "id") "<-[" rel "]-(v) RETURN DISTINCT v.id as id,v.url as url, v.title as title, v.label as label, v.incoming as incoming")]
    (cy/tquery conn query-str {:id code})))

(defn query-common-nodes-from
  "Given a starting node code (common-code) and a node code-from to query from,
  it returns the information for all nodes that code-from links out to that are
  also related to the starting node code in any direction."
  ([conn ^String common-code ^String code-from]
   (query-common-nodes-from conn common-code code-from :LINKSTO 1000))
  ([conn ^String common-code ^String code-from rel incoming-link-limit]
    ; MATCH (n:Anime {id:”Anime/CowboyBebop”})--(m:Main {id:”Main/NoHoldsBarredBeatdown”})-[r:LINKSTO]->(o)--(n) WHERE o.incoming < 1000 RETURN DISTINCT o;
   (let [query-str (str "MATCH "
                        (id-to-match "n" common-code "idn")
                        "--"
                        (id-to-match "m" code-from "idm")
                        "-[" rel "]->(o)--(n) WHERE o.incoming < {limit} "
                        "RETURN DISTINCT o.id as id, o.url as url, o.label as label, o.title as title"
                        )]
     (cy/tquery conn query-str {:idn common-code :idm code-from :limit incoming-link-limit})))
  )


;
; Node creation and tagging
;


(defn create-node!
  "Creates a node from a connection with a label"
  [conn label data-items]
  (let [node (p/p :nn-create (nn/create conn (timestamp-create data-items)))]
    (do
      (p/p :nl-add (nl/add conn node label))
      node)))

(defn merge-node!
  "Updates an existing node, replacing all data items with the ones received,
  and retrieves the existing node."
  [conn ^long id data-items]
  (let [merged (-> (nn/get conn id) (:data) (merge data-items) (timestamp-update))]
    (do
      (p/p :nn-update (nn/update conn id merged))
      (p/p :nn-get (nn/get conn id)))))                     ; Notice that we get it again to retrieve the updated values

(defn create-or-merge-node!
  "Creates a node from a connection with a label. If a node with the id
  already exists, label is ignored and the data-items are merged with
  the existing ones.

  Data-items is expected to include the label."
  [conn data-items]
  (let [existing (query-by-id conn (:id data-items))
        id       (get-in existing [:metadata :id])]
    (if (empty? existing)
      (create-node! conn (:label data-items) data-items)
      (merge-node! conn id data-items))))

(defn create-or-retrieve-node!
  "Creates a node from a connection with a label. If a node with the id
  already exists, the node is retrieved and returned."
  [conn data-items]
  (let [existing (query-by-id conn (:id data-items))
        id       (get-in existing [:metadata :id])]
    (if (empty? existing)
      (create-node! conn (:label data-items) data-items)
      (nn/get conn id))))



;
; Relationship functions
;

(defn relate-nodes!
  "Links two nodes by a relationship if they aren't yet linked"
  [conn relationship n1 n2]
  (p/p :nrl-maybe-create (nrl/maybe-create conn n1 n2 relationship)))



; TODO: Add transaction support

; SNIPPETS
; Update code:
; MATCH (v:Main) SET v.nextupdate = 1426172758403;