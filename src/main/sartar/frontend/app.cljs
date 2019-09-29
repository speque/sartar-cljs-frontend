(ns sartar.frontend.app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [sartar.frontend.components.question :refer [question-component]]
            [sartar.frontend.components.results :refer [results-component]]
            [reagent.core :as r]
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

(defn- randomize-answers []
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
      "r" (randomize-answers)
      :default)))

(defn app []
  (let [qs @questions
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
           "Next"]]
         [:section.section
          [:div.buttons.is-centered
           [:button.button.is-info.is-outlined
            {:on-click #(randomize-answers)}
            "Randomize"]
           [:button.button.is-success.is-outlined
            {:class (when @is-submitting "is-loading")
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
