(ns tropology.s3
  (:require [amazonica.aws.s3 :as s3]
            [clojure.string :refer [lower-case]]
            [environ.core :refer [env]]
            [taoensso.timbre.profiling :as prof]
            [taoensso.timbre :as timbre])
  (:import (com.amazonaws.util IOUtils)))


(def settings (:s3 env))

(defn put-string!
  "Puts a string value to the configured S3 bucket associated with a key"
  [name to-put]
  (let [bytes  (.getBytes (or to-put "") "UTF-8")
        stream (java.io.ByteArrayInputStream. bytes)
        ]
    (s3/put-object settings
                   :bucket-name (:bucket-name settings)
                   :key name
                   :input-stream stream
                   :metadata {:content-length (count bytes)}
                   :return-values "ALL_OLD")
    ))

(defn get-string
  "Returns an object identified by a name as a string. Returns nil if
  there is an error."
  [name]
  (try
    (when (not-empty name)
      (->>
        (s3/get-object settings
                       (:bucket-name settings)
                       name)
        :input-stream
        IOUtils/toString
        ))
    (catch Exception e
      (timbre/error "Exception on get-string" e)
      nil)))
