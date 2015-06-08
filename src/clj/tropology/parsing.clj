(ns tropology.parsing
  (:require [clojure.string :refer [lower-case]]
            [clojurewerkz.urly.core :as u]
            [com.numergent.url-tools :as ut]
            [net.cgrand.enlive-html :as e]
            [taoensso.timbre :as timbre]
            [tropology.base :as b]
            [tropology.db :as db]
            [taoensso.timbre.profiling :as prof]
            ))


(def ignored-categories (set ["administrivia" "tropers"]))
(def ignored-pages (->> ["Main/Administrivia"
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
                         "Main/WikiSandbox"]
                        (map lower-case)
                        set))


;
; Transformation functions
;

;
; Enlive transformation functions
;

(defn enlive->hiccup
  [el]
  (if-not (string? el)
    (->> (map enlive->hiccup (:content el))
         (concat [(:tag el) (:attrs el)])
         (keep identity)
         vec)
    el))

(defn html->enlive
  [html]
  (first (e/html-resource (java.io.StringReader. html))))

(defn html->hiccup [html]
  (-> html
      html->enlive
      enlive->hiccup))




;
; Content functions
;

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
  (let [html (slurp url)
        res  (-> html java.io.StringReader. e/html-resource)]
    {:url url :res res :html html}))



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
  ; Remove the query from the URL since TVTropes will include any parameters
  ; passed on the og:url, but does not actually consider them.
  (let [og-url  (-> (content-from-meta res "og:url") ut/without-query)
        display (b/display-from-url og-url)
        code    (b/code-from-url og-url)]
    {:code        code
     :category    (b/category-from-code code)
     :url         og-url
     :is-redirect false                                     ; Nodes have to be explicitly tagged as being redirects
     :has-error   false
     :display     display
     :title       (content-from-meta res "og:title")
     :image       (content-from-meta res "og:image")
     :description (content-from-meta res "og:description")
     :type        (-> (content-from-meta res "og:type") (ut/if-nil ""))}))

(defn node-data-from-url
  "Returns a map with the metadata we can infer about a new from its URL.
  Assumes the url string conforms to the defined base-url, or will return nil."
  [^String url]
  (if (is-valid-url? url)
    (let [display (b/display-from-url url)
          code    (b/code-from-url url)]
      {:category    (b/category-from-code code)
       :code        code
       :display     display
       :url         url
       :is-redirect false                                   ; Nodes have to be explicitly tagged as being redirects
       :has-error   false
       })
    nil))

(defn get-wiki-links
  "Obtains the wiki links from a html-resource.  Assumes that there will be a
   meta tag with property og:url where it can get the site url from."
  ([res]
   (let [og-host (ut/host-string-of (content-from-meta res "og:url"))]
     (get-wiki-links res og-host)))
  ([res host]
   (->>
     (e/select res [:#wikitext :a.twikilink])
     (remove #(= "nofollow" (get-in % [:attrs :rel])))
     (pmap #(get-in % [:attrs :href]))
     (pmap #(u/resolve host %))
     (pmap lower-case)
     (distinct)
     (filter is-valid-url?)
     (prof/p :get-wiki-links)
     )))


(defn get-tropes
  "Returns a set of tropes list items from a page resource. It looks
  for the list items under #wikitext that are not a sublist.

  We do this because often trope list items contain sublists themselves,
  and we don't want to consider them separate tropes. We can't just
  select for all the li inside #wikitext, since sometimes there's
  folders or other nesting elements.

  It doesn't seem like we can do an difference between two selectors,
  so I'm just returning it as a clojure set.  This will change the
  order in which they appeared on the page, but that is not important.
  "
  [res]
  (let [all-items (-> (e/select res [:#wikitext :li]) set)
        nested    (-> (e/select res [:#wikitext :li :> :ul :> :li]) set)]
    (clojure.set/difference all-items nested)
    ))


(defn element-replace-href
  "Given a map for an :a element, if the href matches the pattern of
  a valid trope node, we replace it with an internal URL of our own
  so that we can detect clicks on it; and otherwise replace the link
  altogether with the contents."
  [a]
  (let [url (get-in a [:attrs :href])]
    (if (is-valid-url? url)
      (-> a
          (assoc-in [:attrs :href] (b/code-from-url url))
          (assoc-in [:attrs :title] (b/code-from-url url)))
      (:content a))
    ))

(defn process-links
  "Given an enlive element, it returns a map containing the element
  converted into a hiccup structure, a list of links in the element,
  and a text version.

  See https://github.com/cgrand/enlive/wiki/Getting-started#using-at"
  ([element]
   (process-links element element-replace-href))
  ([element f-replace]
   (let [res     (e/at element [:a] f-replace)
         spanned (map #(merge % {:tag :span}) res)
         ]
     {:hiccup (enlive->hiccup (first spanned))
      :links  (->> (e/select element [:a.twikilink])
                   (map #(get-in % [:attrs :href])))
      :text   (->> spanned first e/emit* (apply str))
      }))
  )




(defn save-page-links!
  "Saves all page links to the database. It expects a hashmap with two
  paramets: :url for the originally requested URL, and :res for the resulting
  html-resource."
  [{:keys [url res html]}]
  (->>
    (let [node  (-> (node-data-from-meta res) db/timestamp-next-update (merge {:html html}))
          redir {:is-redirect (not= (lower-case (ut/without-query url)) (lower-case (:url node)))
                 :redirector  (node-data-from-url url)}
          links (get-wiki-links res b/base-host)
          ]
      (db/create-page-and-links! node :LINKSTO (pmap node-data-from-url links) redir))
    (prof/p :save-page-links))
  )


(defn log-node-exception!
  "Logs an exception for a url record"
  [^String url ^Throwable t]
  (let [update-data (-> (node-data-from-url url)
                        (select-keys [:code :category :url])
                        (assoc :has-error true :error (.getMessage t)))]
    (timbre/error (str "Exception on " url " : " (.getMessage t)))
    (doall (db/log-error! update-data))))


(defn record-page!
  "Attempts to obtain the links from a url and save them.
  If there's an exception, it marks the url as having an error.
  Created this because at least one page is causing a 'too many redirects' error.

  An optional second parameter allows us to indicate the page's provenance,
  for instance its original URL in case we're loading it from a local dataset
  but originally obtained it by crawling."
  ([url]
   (record-page! url url))
  ([url provenance]
   (try
     (->> (->
            (load-resource-url url)
            (assoc :url provenance))
          (save-page-links!))
     (catch Throwable t
       ; (.printStackTrace t)
       (if (-> t .getMessage (ut/if-empty "") (.contains "TransientError"))
         (timbre/trace (str "Transient error on " url ", not marking to retry. "))
         (log-node-exception! provenance t))))))


(defn crawl-and-update!
  [limit]
  (->> (db/query-nodes-to-crawl limit)
       ; We may get transactions aborting if we parallelize too many requests.
       ;
       ; While we could just take advantage of how we handle transient errors
       ; and let them be retried later, that would require pinging TVTropes
       ; again for the file. I'm leaving as is for now to avoid flooding them
       ; with requests.
       (map record-page!)
       doall
       (prof/profile :trace :Crawl)))
