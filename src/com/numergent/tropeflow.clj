(ns com.numergent.tropeflow
  (:require [clojure.string :as s]
            [clojurewerkz.urly.core :as u]
            [com.numergent.url-tools :as ut]
            [com.numergent.tropeflow.db :as db]
            [net.cgrand.enlive-html :as e])
  (:import (java.net URL)))


; Alternate namespace: tropemagnet

(defn load-resource-url [url]
  (-> url URL. e/html-resource))

; (def sample-res (load-resource-url "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop"))


(def base-path "/pmwiki/pmwiki.php/")
(def base-url (str "http://tvtropes.org" base-path))

(defn content-from-meta
  "Receives a html-resource and returns the content attribute of a meta type indexed by a property."
  [res property]
  (-> (e/select res [[:meta (e/attr= :property property)]]) first (get-in [:attrs :content])))

(defn label-from-id
  "Returns the node label from a node id. Node id is expected to be of the
  form Category/SubCategory.

  Previously I used the og-type as the node label, but a lot of them are too
  generic. For instance, 'article' is used both for the main trope articles as
  well as for the articles on different genres, whereas the root is more
  specific to the topic at hand.

  As an example:

  http://tvtropes.org/pmwiki/pmwiki.php/ComicBook/SpiderManLovesMaryJane

  has the og:type 'article', but ComicBook is a lot more descriptive.
  "
  [^String id]
  (-> id (s/split #"/") first (ut/if-empty "Unknown")))

(defn node-data-from-meta
  "Returns the relevant metadata of a html-resource as a map, including things
  we care about like the node label."
  [res]
  (let [og-url (content-from-meta res "og:url")
        og-host (ut/host-string-of og-url)
        og-title (content-from-meta res "og:title")
        og-image (content-from-meta res "og:image")
        og-type (-> (content-from-meta res "og:type") (ut/if-nil ""))
        id (-> (u/path-of og-url) (s/replace base-path ""))
        label (label-from-id id)]
    {:url   og-url
     :host  og-host
     :title og-title
     :image og-image
     :type  og-type
     :id    id
     :label label}))

(defn node-data-from-url
  "Returns a map with the metadata we can infer about a new from its URL.
  Assumes the url string conforms to the defined base-url, or will return nil."
  [^String url]
  (if (.startsWith url base-url)
    (let [og-host (ut/host-string-of url)
          id (-> (u/path-of url) (s/replace base-path ""))
          label (label-from-id id)]
      {:label label
       :id    id
       :host  og-host
       :url   url})
    nil))

(defn get-wiki-links
  "Obtains the wiki links from a html-resource.  Assumes that there will be a
   meta tag with property og:url where it can get the site url from."
  ([res]
   (let [og-url (content-from-meta res "og:url")
         og-host (ut/host-string-of og-url)]
     (get-wiki-links res og-host)))
  ([res host]
   (->>
     (e/select res [:a.twikilink])
     (map #(get-in % [:attrs :href]))
     (map #(u/resolve host %))
     (distinct)
     (filter #(.startsWith % base-url)))))

(defn save-page-links
  "Saves all page links to the database"
  [res]
  (let [{label :label :as meta} (node-data-from-meta res)
        conn (db/get-connection)
        node (db/create-or-merge-node conn meta)]
    (->>
      (get-wiki-links res (:host meta))
      (pmap node-data-from-url)
      (pmap #(db/create-or-merge-node conn %))
      (pmap #(db/relate-nodes conn :LINKSTO node %)))))



; Attributes to parse:
; og:url - Base url
; og:type - article type. Will be something like video.tv_show, we should probably capitalize it.  (split "video.tv_show" #"\.")
; og:title - Article title
;
; Root article URL: http://tvtropes.org/pmwiki/pmwiki.php/
; The identifier would be path-of minus /pmwiki/pmwiki.php/