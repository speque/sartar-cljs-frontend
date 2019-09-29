(ns sartar.frontend.components.question
  (:require ["react-markdown" :as ReactMarkdown]
            [sartar.frontend.constants :refer [runes]]))

(defn question-component [q current-answer set-answer-fn]
  (let [{:keys [title description query options input notes]} q]
    [:div.content
     [:h2.title.is-2 title]
     [:> ReactMarkdown description]
     (when query [:h4.title.is-4 query])
     (when (seq options)
       [:div.control
        (map-indexed
          (fn [idx option]
            (let [{:keys [title explanation rune]} option]
              [:<> {:key (str "option-" idx)}
               (when explanation [:p explanation])
               [:label.radio
                [:input {:type      "radio"
                         :name      "answer"
                         :checked   (= current-answer idx)
                         :on-change #(set-answer-fn idx)}]
                [:> ReactMarkdown
                 {:disallowedTypes ["list" "listItem" "paragraph"] :unwrapDisallowed true}
                 title]
                (when rune [:span.runes ((keyword rune) runes)])]]))
          options)])
     (when input
       [:p (:title input)])
     (when notes
       [:> ReactMarkdown notes])
     ]))
