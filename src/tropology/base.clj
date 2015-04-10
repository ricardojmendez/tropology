(ns tropology.base
  (:require [clojure.string :as s]
            [com.numergent.url-tools :as ut]
            [clojurewerkz.urly.core :as u]))

(def base-path "/pmwiki/pmwiki.php/")
(def base-url (str "http://tvtropes.org" base-path))

(def base-label "Article")

(defn category-from-code
  "Returns the node label from a node id, which is expected to be of the form Category/SubCategory.

  Previously I used the og-type as the node label, but a lot of them are too generic.
  For instance, 'article' is used both for the main trope articles as well as for the
  articles on different genres, whereas the root is more specific to the topic at hand.

  As an example:

  http://tvtropes.org/pmwiki/pmwiki.php/ComicBook/SpiderManLovesMaryJane

  has the og:type 'article', but ComicBook is a lot more descriptive.
  "
  [^String id]
  (if (nil? id)
    "Unknown"
    (-> id (s/split #"/") first (ut/if-empty "Unknown"))))

(defn code-from-url [^String url]
  (-> (u/path-of url) (s/replace base-path "")))


