(ns tropology.test.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [tropology.test.core]
            [numergent.test.utils]
            ))


(enable-console-print!)

(doo-tests 'tropology.test.core
           'numergent.test.utils)

