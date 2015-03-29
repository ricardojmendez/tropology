(ns tropefest.parsing
  (:require [clojure.string :as s]
            [clojurewerkz.urly.core :as u]
            [com.numergent.url-tools :as ut]
            [net.cgrand.enlive-html :as e]
            [tropefest.base :as b]
            [tropefest.db :as db])
  (:import (java.net URI)))


(defn load-resource-url [url]
  "Loads a html-resource from a URL. Returns a map with the original :url and
  :res for the resource"
  (let [res (-> url URI. e/html-resource)]
    {:url url :res res}))

; (def sample-res (load-resource-url "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop"))

(defn content-from-meta
  "Receives a html-resource and returns the content attribute of a meta type indexed by a property."
  [res property]
  (-> (e/select res [[:meta (e/attr= :property property)]]) first (get-in [:attrs :content])))

; Attributes to parse:
; og:url - Base url
; og:type - article type. Will be something like video.tv_show, we should probably capitalize it.  (split "video.tv_show" #"\.")
; og:title - Article title
;
; Root article URL: http://tvtropes.org/pmwiki/pmwiki.php/
; The identifier would be path-of minus /pmwiki/pmwiki.php/



(defn node-data-from-meta
  "Returns the relevant metadata of a html-resource as a map, including things
  we care about like the node label."
  [res]
  (let [og-url (content-from-meta res "og:url")
        id (-> (u/path-of og-url) (s/replace b/base-path ""))]
    {:id         id
     :label      (b/label-from-id id)
     :url        og-url
     :isredirect false                                      ; Nodes have to be explicitly tagged as being redirects
     :host       (ut/host-string-of og-url)
     :title      (content-from-meta res "og:title")
     :image      (content-from-meta res "og:image")
     :type       (-> (content-from-meta res "og:type") (ut/if-nil ""))}))

(defn node-data-from-url
  "Returns a map with the metadata we can infer about a new from its URL.
  Assumes the url string conforms to the defined base-url, or will return nil."
  [^String url]
  (if (.startsWith url b/base-url)
    (let [id (-> (u/path-of url) (s/replace b/base-path ""))]
      {:label      (b/label-from-id id)
       :id         id
       :host       (ut/host-string-of url)
       :url        url
       :isredirect false                                    ; Nodes have to be explicitly tagged as being redirects
       })
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
     (filter #(.startsWith % b/base-url)))))


(defn save-page-links!
  "Saves all page links to the database. It expects a hashmap with two
  paramets: :url for the originally requested URL, and :res for the resulting
  html-resource."
  ([pack]
   (save-page-links! (db/get-connection) pack))
  ([conn {url :url res :res}]
   (let [meta (-> (node-data-from-meta res) db/timestamp-next-update)
         is-redirect (not= url (:url meta))
         meta-rd (assoc meta :isredirect is-redirect)
         node (db/create-or-merge-node! conn meta-rd)]
     (db/mark-if-redirect! conn url is-redirect)            ; Feels like a hack, review.
     (->>
       (get-wiki-links res (:host meta))
       (pmap node-data-from-url)
       (map #(db/create-or-retrieve-node! conn %))          ; Nodes are only retrieved when linking to, not updated
       (map #(db/relate-nodes! conn :LINKSTO node %))       ; Add link
       doall))                                              ; I could probably change this for a doseq to use less RAM
    ))


(defn log-node-exception!
  "Logs an exception for a url record"
  [conn ^String url ^Throwable t]
  (let [update-data (-> (node-data-from-url url)
                        (select-keys [:id :label])
                        (assoc :error (.getMessage t)))]
    (doall (db/create-or-merge-node! conn update-data))))

(defn record-page!
  "Attempts to crawl a url. If there's an exception, it marks the url as having an error.
  Created this because at least one page is causing a 'too many redirects' error."
  [conn url]
  (try
    (->> url
         (map load-resource-url)
         (pmap #(save-page-links! conn %))
         doall)
    (catch Throwable t (log-node-exception! conn url t))))


(defn crawl-and-update!
  [conn limit]
  (->> (db/query-nodes-to-crawl conn limit)
       (record-page! conn)))