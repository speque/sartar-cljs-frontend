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
    (map-indexed 
      (fn [idx option]
        ^{:key (str "option-" idx)} [:li (:title option)])
      (:options q))
    ]
  ])

(defn- dec-question! []
  (when (> @active-question 0)
    (swap! active-question dec)))

(defn- inc-question! []
  (when (< @active-question (dec (count @questions)))
    (swap! active-question inc)))

(defn- key-handler [event]
  
  (let [key-name (.-key event)]
    (case key-name
      "ArrowLeft"  (dec-question!)
      "ArrowRight" (inc-question!)
      :default)))

(defn app []
  (let [qs @questions
        active-question-index @active-question]
    (if (seq qs)
      [:div
      [question-component (nth qs active-question-index)]
        [:button.button.is-primary 
          {:disabled (= active-question-index 0)
           :on-click #(swap! active-question dec)}
          "Previous"]
        [:button.button.is-primary
          {:disabled (= active-question-index (dec (count qs)))
           :on-click #(swap! active-question inc)}
          "Next"]
      ]
      [:div "No questions"])))

(defn stop []
  (js/console.log "Stopping...")
  (js/document.removeEventListener "keydown" key-handler))

(defn start []
  (js/console.log "Starting...")
  (js/document.addEventListener "keydown" key-handler)
  (r/render [app] (.getElementById js/document "app"))
  (go 
    (let [response (<! (http/get "http://localhost:3001" {:with-credentials? false}))]
      (reset! questions (:questions (:body response))))))
  
(defn ^:export init []
  (start))
