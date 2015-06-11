(ns numergent.utils)

(defn in-seq?
  "Evaluates if x is in the sequence s"
  [s x]
  (some? (some #{x} s)))


(defn if-empty
  "Returns a if neither nil or empty, b if otherwise"
  [a b]
  (if (or (nil? a) (empty? a)) b a))
