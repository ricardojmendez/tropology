(ns com.numergent.url-tools
  (:require [clojurewerkz.urly.core :as u]))


(defn host-string-of
  "Returns the host string for a url, including protocol and trailing slash.
  For now assumes that there's no need for a port."
  [url]
  (str (u/protocol-of url) "://" (u/host-of url) "/"))


; TODO: Should probably get to doing tests for these.

