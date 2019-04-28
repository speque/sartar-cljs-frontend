(ns sartar.frontend.app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [sartar.frontend.components.question :refer [question-component]]
            [sartar.frontend.components.results :refer [results-component]]
            [reagent.core :as r]
            [goog.string :as gstring]
            [cljs-http.client :as http]
            [re-frame.core :as rf]
            [re-frame.registrar :as rf.reg]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [cljs.core.async :refer [<!]]))

(rf/reg-event-db
  :initialize
  (fn [_ _]
    (js/console.log "Initializing...")
    {:questions []
     :active-question 0
     :answers []
     :inputs []
     :results {}
     :is-submitting false}))

(rf/reg-event-db
  :questions-fetched
  (fn [db [_ questions]]
    (js/console.log "Questions fetched")
    (let [number-of-questions (count questions)]
      (assoc db
        :questions questions
        :answers (vec (repeat number-of-questions -1))
        :inputs (vec (repeat number-of-questions nil))))))

(rf/reg-event-db
  :inc-question
  (fn [db]
    (let [number-of-questions (count (:questions db))
          active-question (:active-question db)]
      (if (< active-question (dec number-of-questions))
        (update db :active-question inc)
        db))))

(rf/reg-event-db
  :dec-question
  (fn [db]
    (let [active-question (:active-question db)]
      (if (> active-question 0)
        (update db :active-question dec)
        db))))

(rf/reg-event-db
 :set-question
 (fn [db [_ new-index]]
    (assoc db :active-question new-index)))

(rf/reg-event-db
  :randomize
  (fn [db]
    (assoc db :answers (vec (map (fn [q] 
                                   (let [options (:options q)
                                         number-of-options (count options)] 
                                     (if (> number-of-options 0)
                                       (rand-int number-of-options)
                                       -1))) 
                                 (:questions db))))))

(rf/reg-event-db
  :set-answer
  (fn [db [_ index answer]]
    (update db :answers #(assoc % index answer))))

(rf/reg-event-db
  :set-submit_status
  (fn [db [_ status]]
    (assoc db :is-submitting status)))

(rf/reg-event-db
  :good-post-result
  (fn [db]
    (js/console.log "Good post!")
    (assoc db :is-submitting false)))

(rf/reg-event-db
  :bad-post-result
  (fn [db]
    (js/console.log "Bad post!")
    (assoc db :is-submitting false)))

(defn- mask-answers [qs unmasked-answers]
  (js/console.log "masking")
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

(rf/reg-event-fx
  :submit
  (fn [{db :db} _]
    (let [qs (:questions db)
          as (:answers db)
          inputs (:inputs db)
          masked-answers (mask-answers qs as)]
      (js/console.log "submitting")
      {:http-xhrio {:method          :post
                    :uri             "http://localhost:3001"
                    :params          {:answers masked-answers :inputs inputs}
                    :timeout         5000
                    :format          (ajax/json-request-format)
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [:good-post-result]
                    :on-failure      [:bad-post-result]}
       :db (assoc db :is-submitting true)})))

(rf/reg-sub
  :questions
  (fn [db _]
    (:questions db)))
(rf/reg-sub
  :answers
  (fn [db _]
    (:answers db)))
(rf/reg-sub
 :results
 (fn [db _]
   (:results db)))
(rf/reg-sub
  :active-question
  (fn [db _]
    (:active-question db)))
(rf/reg-sub
  :submit-status
  (fn [db _]
    (:is-submitting db)))

(defn- key-handler [event]
  (let [key-name (.-key event)]
    (case key-name
      "ArrowLeft"  (rf/dispatch [:dec-question])
      "ArrowRight" (rf/dispatch [:inc-question])
      "r" (rf/dispatch [:randomize])
      :default)))

(defn- submitting-disabled? [qs current-answers]
  (let [statuses (map-indexed
                  (fn [i answer]
                    (let [q (nth qs i)
                          disabled-by (:disabled_by q)]
                      (cond
                        (= answer -1) (let [has-no-options (= 0 (count (:options q)))
                                            is-disabled (and
                                                         disabled-by
                                                         (= (nth current-answers (first disabled-by)) (second disabled-by)))]
                                        (cond
                                          has-no-options {:valid true :case "Answer -1 but has no options"}
                                          (not is-disabled) {:valid false :case "Answer -1, has options but is not disabled"}
                                          :default {:valid true :case "Answer -1, has options but is disabled"}))
                        (and (>= answer 0) (let [disabled-by-option (:disabled_by (nth (:options q) answer))
                                                 is-disabled (and disabled-by-option (=
                                                                                      (nth current-answers (first disabled-by-option))
                                                                                      (second disabled-by-option)))]))
                        {:valid false :case "Answered but the chosen option is disabled by another answer"}
                        :default {:valid true :case "Default"})))
                  current-answers)]
    (some #(not (:valid %)) statuses)))
    
(defn app []
  ;; TODO break these
  (let [qs @(rf/subscribe [:questions])
        number-of-questions (count qs)
        active-question-index @(rf/subscribe [:active-question])
        current-answers @(rf/subscribe [:answers])
        current-results @(rf/subscribe [:results])]
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
             #(rf/dispatch [:set-answer active-question-index %])]))

        [:section.section
         [:nav.pagination.is-centered {:role "navigation" :aria-label "pagination"}
          [:a.pagination-previous
           {:disabled (= active-question-index 0)
            :on-click #(rf/dispatch [:dec-question])}
           "Previous"]
          [:a.pagination-next
           {:disabled (= active-question-index (dec (count qs)))
            :on-click #(rf/dispatch [:inc-question])}
           "Next"]
          [:ul.pagination-list
           [:li
            [:a.pagination-link
             {:class (when (= 0 active-question-index) "is-current")
              :on-click #(rf/dispatch [:set-question 0])
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
              :on-click #(rf/dispatch [:set-question (dec number-of-questions)])
              :aria-label "Viimeinen"}
             number-of-questions]]]]


         [:section.section
          [:div.buttons.is-centered
           [:button.button.is-info.is-outlined
            {:on-click #(rf/dispatch [:randomize])}
            "Randomize"]
           [:button.button.is-success.is-outlined
            {:class (when @(rf/subscribe [:submit-status]) "is-loading")
             :disabled (submitting-disabled? qs current-answers)
             :on-click #((rf/dispatch [:submit]))}
            "Submit"]]]]]

       [:div.content "No questions"])

     (when (not-empty current-results)
       [results-component current-results])]))

(defn stop []
  (js/console.log "Stopping...")
  (js/document.removeEventListener "keydown" key-handler))

(defn start []
  (js/console.log "Starting....")
  (rf/dispatch-sync [:initialize])
  (js/document.addEventListener "keydown" key-handler)
  (r/render [app] (.getElementById js/document "app"))
  (go
    (let [response (<! (http/get "http://localhost:3001" {:with-credentials? false}))
          fetched-questions (:questions (:body response))]
      (rf/dispatch [:questions-fetched fetched-questions]))))

(defn ^:export init []
  (start))
