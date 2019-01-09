(ns sartar.frontend.app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(defonce questions (r/atom []))
(defonce active-question (r/atom 0))

(defn question-component [q]
  [:div.q 
    [:h2.subtitle.is-h2 (:title q)] 
    [:p (:query q)] 
    [:p (:description q)]
    [:ul
      (for [option (:options q)]
        [:li (:title option)])
    ]
  ])

(defn app []
  (let [qs @questions
        active-question-index @active-question]
    (if (seq qs)
      [:div
      [question-component (nth qs active-question-index)]
        [:input {:type "button" 
                 :value "Previous!"
                 :disabled (= active-question-index 0)
                 :on-click #(swap! active-question dec)}]
        [:input {:type "button" 
                 :value "Next!"
                 :disabled (= active-question-index (dec (count qs)))
                 :on-click #(swap! active-question inc)}]
      ]
      [:div "No questions"])))

(defn stop []
  (js/console.log "Stopping..."))

(defn start []
  (js/console.log "Starting...")
  (r/render [app]
            (.getElementById js/document "app"))
  (go 
    (let [response (<! (http/get "http://localhost:3000" {:with-credentials? false}))]
      (reset! questions (:questions (:body response))))))
  
(defn ^:export init []
  (start))
