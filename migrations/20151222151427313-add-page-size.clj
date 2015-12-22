;; migrations/20151222151427313-add-page-size.clj

(defn up []
  ["ALTER TABLE pages ADD COLUMN size INT NOT NULL DEFAULT 0"])

(defn down []
  ["ALTER TABLE pages DROP COLUMN IF EXISTS size"])
