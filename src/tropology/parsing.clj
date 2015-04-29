(ns tropology.parsing
  (:require [clojure.string :as s]
            [clojurewerkz.urly.core :as u]
            [com.numergent.url-tools :as ut]
            [net.cgrand.enlive-html :as e]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.profiling :as p]
            [tropology.base :as b]
            [tropology.db :as db]
            [taoensso.timbre.profiling :as prof])
  (:import (java.net URI)))


(def ignored-categories (set ["Administrivia" "Tropers"]))
(def ignored-pages (set ["Main/Administrivia"
                         "Main/Cliche"
                         "Main/GoodStyle"
                         "Main/HomePage"
                         "Main/ListOfShowsThatNeedSummary"
                         "Main/LostAndFound"
                         "Main/PageTemplates"
                         "Main/RuleOfCautiousEditingJudgement"
                         "Main/TextFormattingRules"
                         "Main/ThereIsNoSuchThingAsNotability"
                         "Main/Trope"
                         "Main/Tropes"
                         "Main/WecomeToTVTropes"
                         "Main/WikiMagic"
                         "Main/Wikipedia"
                         "Main/WikiSandbox"]))

(defn is-valid-url?
  "Evaluates if a URL is valid for us to crawl or not"
  [url]
  (let [code (b/code-from-url url)
        cat  (b/category-from-code code)]
    (and
      (.startsWith url b/base-url)
      (empty? (some #{cat} ignored-categories))
      (empty? (some #{code} ignored-pages))))
  )


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
        code   (b/code-from-url og-url)]
    {:code       code
     :category   (b/category-from-code code)
     :url        og-url
     :isRedirect false                                      ; Nodes have to be explicitly tagged as being redirects
     :hasError   false
     :host       (ut/host-string-of og-url)
     :title      (content-from-meta res "og:title")
     :image      (content-from-meta res "og:image")
     :type       (-> (content-from-meta res "og:type") (ut/if-nil ""))}))

(defn node-data-from-url
  "Returns a map with the metadata we can infer about a new from its URL.
  Assumes the url string conforms to the defined base-url, or will return nil."
  [^String url]
  (if (is-valid-url? url)
    (let [code (b/code-from-url url)]
      {:category   (b/category-from-code code)
       :code       code
       :host       (ut/host-string-of url)
       :url        url
       :isRedirect false                                    ; Nodes have to be explicitly tagged as being redirects
       :hasError   false
       })
    nil))

(defn get-wiki-links
  "Obtains the wiki links from a html-resource.  Assumes that there will be a
   meta tag with property og:url where it can get the site url from."
  ([res]
   (let [og-url  (content-from-meta res "og:url")
         og-host (ut/host-string-of og-url)]
     (get-wiki-links res og-host)))
  ([res host]
   (->>
     (e/select res [:#wikitext :a.twikilink])
     (map #(get-in % [:attrs :href]))
     (map #(u/resolve host %))
     (distinct)
     (filter is-valid-url?)
     )))


(defn save-page-links!
  "Saves all page links to the database. It expects a hashmap with two
  paramets: :url for the originally requested URL, and :res for the resulting
  html-resource."
  ([pack]
   (save-page-links! (db/get-connection) pack))
  ([conn {url :url res :res}]
   (let [node  (-> (node-data-from-meta res) db/timestamp-next-update)
         redir {:isRedirect (not= url (:url node))
                :redirector (b/code-from-url url)}
         links (get-wiki-links res (:host node))
         ]
     (->>
       (db/create-page-and-links! conn node :LINKSTO (map b/code-from-url links) redir)
       (prof/p :save-page-links)))
    ))


(defn log-node-exception!
  "Logs an exception for a url record

  DEPRECATED, MUST DO IN A BETTER PERFORMANT MANNER.
  "
  [conn ^String url ^Throwable t]
  (let [update-data (-> (node-data-from-url url)
                        (select-keys [:code :category])
                        (assoc :hasError true :error (.getMessage t)))]
    (timbre/error (str "Exception on " url " : " (.getMessage t)))
    (doall (db/create-or-merge-node! conn update-data))
    ))

(defn record-page!
  "Attempts to obtain the links from a url and save them.
  If there's an exception, it marks the url as having an error.
  Created this because at least one page is causing a 'too many redirects' error.

  An optional second parameter allows us to indicate the page's provenance,
  for instance its original URL in case we're loading it from a local dataset
  but originally obtained it by crawling."
  ([conn url]
   (record-page! conn url url))
  ([conn url provenance]
   (try
     (->> (->
            (load-resource-url url)
            (assoc :url provenance))
          (save-page-links! conn))
     (catch Throwable t
       (if (-> t .getMessage (.contains "TransientError"))
         (timbre/trace (str "Transient error on " url ", not marking to retry. "))
         (log-node-exception! conn provenance t))))))


(defn crawl-and-update!
  [conn limit]
  (->> (db/query-nodes-to-crawl conn limit)
       ; We may get transactions aborting if we parallelize too many requests.
       ;
       ; While we could just take advantage of how we handle transient errors
       ; and let them be retried later, that would require pinging TVTropes
       ; again for the file. I'm leaving as is for now to avoid flooding them
       ; with requests.
       (map #(record-page! conn %))
       doall
       (prof/profile :trace :Crawl)))
