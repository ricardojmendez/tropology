(ns tropology.test.core
  (:require [cljs.test :refer-macros [deftest testing is]]
            [tropology.core :as core]))


;
; Trope processor tests
;

(deftest test-process-span
  (is (= [:span "Some text"]
         (core/process-span [:span "Some text"])))
  (is (nil? (core/process-span [:span {:class "notelabel"} "Some span"]))) ; notelabel spans are discarded
  (is (= [:span {:class "highlight" :rel "arel"} "Some span here"]
         (core/process-span [:span
                             {:class "highlight" :id "id" :onclick "somefunction()" :rel "arel"}
                             "Some span here"]))))

(deftest test-process-style
  (is (= [:p {:style {"display" "none"}} "A paragraph"]
         (core/process-style [:p {:style "display:none"} "A paragraph"])
         ))
  (is (= [:p {:style {"display" "none"}} "A paragraph"]
         (core/process-style [:p {:style "display:none" :onclick "somefunction()"} "A paragraph"])
         ))
  (is (= [:p {:style {"display" "none" "height" " 21px"}} "A paragraph"]
         (core/process-style [:p {:style "display:none;height: 21px"} "A paragraph"])
         ))
  )

(deftest test-process-a
  (is (= [:a {:href "anime/samuraiflamenco"} "The text"]    ; Only twikilinks are changed
         (core/process-a [:a {:href "anime/samuraiflamenco"} "The text"] nil)))
  (is (= [:a "The text"]
         (core/process-a [:a "The text"] nil)))
  (let [altered (core/process-a [:a {:class "twikilink" :href "anime/samuraiflamenco"} "The text"] nil)
        tag (first altered)
        attrs (second altered)
        text (nth altered 2)]
    (is (= :a tag))
    (is (= "The text" text))
    (is (map? attrs))
    (is (= "twikilink" (:class attrs)))                     ; The class isn't touched
    (is (nil? (:href attrs)))                               ; The href was removed
    (is (some? (:on-click attrs)))                          ; We have a new on-click function
    )
  )

(deftest test-process-trope
  (let [trope [:span
               [:div
                [:p "This is an innocent paragraph"]
                [:p "This is just as innocent, but "
                 [:a {:class "twikilink" :href "main/anime"} "it contained a link!"] " and some more text"]
                [:span "The end."]]
               [:div
                [:span "Then a span of text"
                 [:span {:class "notelabel"} "with a note"]
                 ", but not really."]
                [:span {:style "white-space: nowrap;text-decoration: none"}
                 "A styled span."
                 [:p {:style "font-size:16px;font-weight:normal"} "With a styled p inside."]
                 [:a {:href "#"} "And a link that's ignored"]
                 ]]
               ]
        result (core/process-trope trope [true])]
    (is (= 3 (count result)))
    (is (= :span (first result)))
    (is (= [:div
            [:span "Then a span of text" nil ", but not really."]
            [:span {:style {"white-space" " nowrap" "text-decoration" " none"}}
             "A styled span."
             [:p {:style {"font-size" "16px" "font-weight" "normal"}} "With a styled p inside."]
             [:a {:href "#"} "And a link that's ignored"]
             ]]
           (nth result 2)))
    (let [link-div (second result)
          a-elem (get-in link-div [2 2])
          attrs (second a-elem)]
      (is (= 4 (count link-div)))
      (is (= :div (first link-div)))
      (is (= [:p "This is an innocent paragraph"]
             (nth link-div 1)))
      (is (= :p (get-in link-div [2 0])))
      (is (= "This is just as innocent, but " (get-in link-div [2 1])))
      (is (= 3 (count a-elem)))
      (is (= :a (first a-elem)))
      (is (map? attrs))
      (is (nil? (:href attrs)))
      (is (some? (:on-click attrs)))
      (is (= " and some more text" (get-in link-div [2 3])))
      (is (= [:span "The end."] (get-in link-div [3])))
      )
    )
  )


