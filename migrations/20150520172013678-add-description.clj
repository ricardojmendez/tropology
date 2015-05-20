;; migrations/20150520172013678-add-description.clj

(defn up []
  ["ALTER TABLE pages ADD COLUMN description text"])

(defn down []
  ["ALTER TABLE pages DROP COLUMN IF EXISTS description"])
