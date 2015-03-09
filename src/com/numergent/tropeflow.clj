(ns com.numergent.tropeflow
  (:require [net.cgrand.enlive-html :as e]
            [com.numergent.url-tools :as ut]
            [clojurewerkz.urly.core :as u])
  (:import (java.net URL)))



(defn load-resource-url [url]
  (-> url URL. e/html-resource))

; (def sample-res (load-resource-url "http://tvtropes.org/pmwiki/pmwiki.php/Anime/CowboyBebop"))

(defn get-wiki-links
  "Obtains the wiki links from a html-resource.  Assumes that there will be a
   meta tag with property og:url where it can get the site url from."
  ([res]
   (let [meta-url (first (e/select res [[:meta (e/attr= :property "og:url")]]))
         og-url (get-in meta-url [:attrs :content])
         og-host (ut/host-string-of og-url)]
     (get-wiki-links res og-host)))
  ([res host]
   (->>
     (e/select res [:a.twikilink])
     (map #(get-in % [:attrs :href]))
     (map #(u/resolve host %))
     (distinct))))

; Attributes to parse:
; og:url - Base url
; og:type - article type
; og:title - Article title
;
; Root article URL: http://tvtropes.org/pmwiki/pmwiki.php/
; The identifier would be path-of minus /pmwiki/pmwiki.php/