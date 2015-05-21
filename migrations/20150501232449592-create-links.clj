;; migrations/20150501232449592-create-links.clj

(defn up []
  [(str "CREATE TABLE links ("
        "from_code varchar(200) not null REFERENCES pages(code), "
        "to_code varchar(200) not null REFERENCES pages(code), "
        "type varchar(20) not null, "
        "data text)")
   "CREATE UNIQUE INDEX links_idx ON links (from_code, to_code, type)"
   "CREATE INDEX links_from ON links (from_code, type)"
   "CREATE INDEX links_to ON links (to_code, type)"
   "ALTER TABLE links ADD PRIMARY KEY USING INDEX links_idx"
   ]
  )

(defn down []
  ["DROP TABLE links"])
