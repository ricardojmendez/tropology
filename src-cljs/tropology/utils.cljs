(ns tropology.utils)

(defn in-seq? [s x]
  (some? (some #{x} s)))

