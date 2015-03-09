(ns enlive-tropes.core
  (:use net.cgrand.enlive-html)
  (:import (java.net URI)))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))





(def links (-> "file:///Users/ricardo/Sources/clojure-tests/enlive-tropes/files/CowboyBebop.html" URI. html-resource
               (select [:a.twikilink])))

(def urls (map #(get-in % [:attrs :href]) links))


(filter #(.startsWith % "/pmwiki/pmwiki.php") urls)