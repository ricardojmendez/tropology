(ns tropology.base
  (:require [clojure.string :as s]
            [clojure.string :refer [lower-case]]
            [numergent.utils :as u]
            [clojurewerkz.urly.core :as urly]
            [taoensso.timbre.profiling :as prof]))

(def base-path "/pmwiki/pmwiki.php/")
(def base-host "http://tvtropes.org/")
(def base-url (str "http://tvtropes.org" base-path))

(def base-label "Article")

(def view-url "/view/")

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
    (-> id (s/split #"/") first (u/if-empty "Unknown"))))

(defn display-from-url [^String url]
  (when url (-> (urly/path-of url) (or "") (s/replace base-path ""))))

(defn code-from-url [^String url]
  (when url (-> url display-from-url lower-case)))


(defn group-pairs
  "Receives a list of maps with from/to keys, and returns a map that where the keys
  are the from elements and the values are a collection of all the to referenced
  by that node"
  ([links]
    (group-pairs links :from-code :to-code))
  ([links from to]
  (->>
    (group-by #(get % from) links)
    (map (fn [kv] [(key kv) (map #(get % to) (val kv))]))
    (into {})
    (prof/p :group-pairs)
    )))


