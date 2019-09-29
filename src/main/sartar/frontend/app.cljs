(ns sartar.frontend.app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [sartar.frontend.components.question :refer [question-component]]
            [sartar.frontend.components.results :refer [results-component]]
            [reagent.core :as r]
            [goog.string :as gstring]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(defonce questions (r/atom []))
(defonce active-question (r/atom 0))
(defonce answers (r/atom []))
(defonce inputs (r/atom []))
(defonce results (r/atom nil))
(defonce is-submitting (r/atom false))

(defn- dec-question! []
  (when (pos? @active-question)
    (swap! active-question dec)))

(defn- inc-question! []
  (when (< @active-question (dec (count @questions)))
    (swap! active-question inc)))

(defn- set-question! [new-active-question]
  (reset! active-question new-active-question))

(defn- randomize-answers! []
  (reset! answers (vec (map #(rand-int (count (:options %))) @questions))))

(defn- mask-answers [qs unmasked-answers]
  (map-indexed
   (fn [idx a]
     (let [q (nth qs idx)
           options (:options q)
           disabled-by (:disabled_by q)]
       (if (or (not (seq options))
               (and
                disabled-by
                (= (nth unmasked-answers (first disabled-by)) (second disabled-by))))
         -1
         a)))
   unmasked-answers))

(defn- submit-answers []
  (go
    (let [qs @questions
          as @answers
          masked-answers (mask-answers qs as)
          response (<! (http/post "http://localhost:3001" {:json-params {:answers masked-answers :inputs @inputs}
                                                           :with-credentials? false}))]
      (reset! is-submitting false)
      (reset! results (:body response)))))

(defn- key-handler [event]
  (let [key-name (.-key event)]
    (case key-name
      "ArrowLeft"  (dec-question!)
      "ArrowRight" (inc-question!)
      "r" (randomize-answers!)
      :default)))

(defn- submitting-disabled? []
  (let [qs @questions
        current-answers @answers
        invalid (keep-indexed
                 (fn [i answer]
                   (let [q (nth qs i)
                         disabled-by (:disabled_by q)]
                     (if (or
                          ; question has not been answered AND
                          ; it has not been disabled by another
                          ; answer in another question
                          (and
                           (< answer 0)
                           (not
                            (and
                             disabled-by
                             (=
                              (nth current-answers (first disabled-by))
                              (second disabled-by)))))
                          ; question has been answered and the chosen
                          ; option has not been disabled by another
                          ; option in another question
                           (let [disabled-by-option (:disabled_by (nth (:options q) answer))]
                             (and
                              (>= answer 0)
                              disabled-by-option
                              (=
                               (nth current-answers (first disabled-by-option))
                               (second disabled-by-option)))))
                       answer
                       nil)))
                 current-answers)]
    (pos? (count (vec invalid)))))

(defn app []
  (let [qs @questions
        number-of-questions (count qs)
        active-question-index @active-question
        current-answers @answers
        current-results @results]
    [:div
     (if (seq qs)
       [:div
        (let [active-question (nth qs active-question-index)
              current-answer (nth current-answers active-question-index)
              disabled-by (:disabled_by active-question)]
          (if (and disabled-by (= (nth current-answers (first disabled-by)) (second disabled-by)))
            [:h2.title.is-2 "Previous answer rendered this question irrelevant"]
            [question-component
             active-question
             current-answer
             (fn [answer] (swap! answers #(assoc % active-question-index answer)))]))

        [:section.section
         [:nav.pagination.is-centered {:role "navigation" :aria-label "pagination"}
          [:a.pagination-previous
           {:disabled (= active-question-index 0)
            :on-click dec-question!}
           "Previous"]
          [:a.pagination-next
           {:disabled (= active-question-index (dec (count qs)))
            :on-click inc-question!}
           "Next"]
          [:ul.pagination-list
           [:li
            [:a.pagination-link
             {:class (when (= 0 active-question-index) "is-current")
              :on-click #(set-question! 0)
              :aria-label "Ensimmäinen"}
             1]]
           (when (> active-question-index 1)
             [:li
              [:span.pagination-ellipsis (gstring/unescapeEntities "&hellip;")]])
           (when (and (> active-question-index 0) (< active-question-index (dec number-of-questions)))
             [:li
              [:a.pagination-link
               {:class "is-current" :aria-label "Nykyinen"}
               (+ 1 active-question-index)]])
           (when (< active-question-index number-of-questions)
             [:li
              [:span.pagination-ellipsis (gstring/unescapeEntities "&hellip;")]])
           [:li
            [:a.pagination-link
             {:class (when (= (dec number-of-questions) active-question-index) "is-current")
              :on-click #(set-question! (dec number-of-questions))
              :aria-label "Viimeinen"}
             number-of-questions]]]]


         [:section.section
          [:div.buttons.is-centered
           [:button.button.is-info.is-outlined
            {:on-click randomize-answers!}
            "Randomize"]
           [:button.button.is-success.is-outlined
            {:class (when @is-submitting "is-loading")
             :disabled (submitting-disabled?)
             :on-click #(do
                          (reset! is-submitting true)
                          (submit-answers))}
            "Submit"]]]]]

       [:div.content "No questions"])

     (if (not-empty current-results)
       [results-component current-results])]))

(defn stop []
  (js/console.log "Stopping...")
  (js/document.removeEventListener "keydown" key-handler))

(defn start []
  (js/console.log "Starting...")
  (js/document.addEventListener "keydown" key-handler)
  (r/render [app] (.getElementById js/document "app"))
  (go
    (let [response (<! (http/get "http://localhost:3001" {:with-credentials? false}))
          fetched-questions (:questions (:body response))
          number-of-questions (count fetched-questions)]
      (reset! answers (vec (repeat number-of-questions -1)))
      (reset! inputs (vec (repeat number-of-questions nil)))
      (reset! questions fetched-questions))))

(defn ^:export init []
  (start))
