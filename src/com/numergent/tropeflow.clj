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

(defn meta-as-map
  "Returns the relevant metadata of a html-resource as a map, including things
  we care about like the node label."
  [res]
  (let [og-url (content-from-meta res "og:url")
        og-host (ut/host-string-of og-url)
        og-title (content-from-meta res "og:title")
        og-image (content-from-meta res "og:image")
        og-type (-> (content-from-meta res "og:type") (ut/if-nil ""))
        main-type (-> og-type (s/split #"\.") first s/capitalize (ut/if-empty "Unknown"))]
    {:url   og-url
     :host  og-host
     :title og-title
     :image og-image
     :type  og-type
     :id    (-> (u/path-of og-url) (s/replace base-path ""))
     :label main-type}))

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
  (let [{label :label :as meta} (meta-as-map res)
        conn (db/get-connection)]
    (db/create-node conn label meta)))



; Attributes to parse:
; og:url - Base url
; og:type - article type. Will be something like video.tv_show, we should probably capitalize it.  (split "video.tv_show" #"\.")
; og:title - Article title
;
; Root article URL: http://tvtropes.org/pmwiki/pmwiki.php/
; The identifier would be path-of minus /pmwiki/pmwiki.php/