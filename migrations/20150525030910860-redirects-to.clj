;; migrations/20150525030910860-redirects-to.clj

(defn up []
  ["ALTER TABLE pages ADD COLUMN redirects_to varchar(200) REFERENCES pages(code)"])

(defn down []
  ["ALTER TABLE pages DROP COLUMN IF EXISTS redirects_to"])
