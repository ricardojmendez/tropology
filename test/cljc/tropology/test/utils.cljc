(ns tropology.test.utils
  #?(:clj
     (:require [tropology.utils :refer :all]
               [clojure.test :refer :all])
     :cljs
     (:require [tropology.utils :refer [in-seq?]]
               [cljs.test :refer-macros [deftest is]]))
  )

(deftest test-in-seq
  (is (in-seq? [0 19 2 3 1 22] 1))
  (is (in-seq? [:href :style :p :a :span] :style))
  (is (false? (in-seq? [:href :style :p :a :span] :on-click)))
  (is (in-seq? [{:a 1 :b 2} {:a 2 :b 3} {:a 4 :b 5}] {:b 3 :a 2}))
  )



