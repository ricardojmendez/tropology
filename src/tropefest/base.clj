(ns tropefest.base
  (:require [clojure.string :as s]
            [com.numergent.url-tools :as ut]))

(def base-path "/pmwiki/pmwiki.php/")
(def base-url (str "http://tvtropes.org" base-path))


(defn label-from-id
  "Returns the node label from a node id, which is expected to be of the form Category/SubCategory.

  Previously I used the og-type as the node label, but a lot of them are too generic.
  For instance, 'article' is used both for the main trope articles as well as for the
  articles on different genres, whereas the root is more specific to the topic at hand.

  As an example:

  http://tvtropes.org/pmwiki/pmwiki.php/ComicBook/SpiderManLovesMaryJane

  has the og:type 'article', but ComicBook is a lot more descriptive.
  "
  [^String id]
  (-> id (s/split #"/") first (ut/if-empty "Unknown")))


