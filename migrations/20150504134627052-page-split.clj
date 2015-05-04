;; migrations/20150504134627052-page-split.clj

(defn up []
  ; Notice that contents does not reference pages as a foreign key.
  ; This is because I'm considering splitting page crawling, which would
  ; fill "contents", from page parsing, which would fill "pages".
  ["CREATE TABLE contents
    (code VARCHAR(200) PRIMARY KEY,
     html text)"
   "INSERT INTO contents (code, html) SELECT code, html FROM pages WHERE html is not null"
   "ALTER TABLE pages DROP COLUMN IF EXISTS html"
   ])

(defn down []
  ["ALTER TABLE pages ADD column html text "
   "UPDATE pages p SET html = c.html FROM contents c WHERE p.code = c.code"
   "DROP TABLE contents"])
