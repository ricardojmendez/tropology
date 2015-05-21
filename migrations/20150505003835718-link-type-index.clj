;; migrations/20150505003835718-link-type-index.clj

(defn up []
  ["CREATE INDEX links_type ON links(type)"])

(defn down []
  ["DROP INDEX links_type"])
