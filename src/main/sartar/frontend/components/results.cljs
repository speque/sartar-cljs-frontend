(ns sartar.frontend.components.results
  (:require ["react-markdown" :as ReactMarkdown]
            [sartar.frontend.constants :refer [runes]]))

(defn results-component [current-results]
  [:div
   [:h4.title.is-4 "Runes"]
   (map-indexed
    (fn [idx [k v]]
      [:p [:span.runes ((keyword k) runes)] v])
    (seq (:rune_modifiers current-results)))
   
   [:h4.title.is-4 "Resources"]
   (map-indexed
    (fn [idx [k v]]
      [:p [:span k] " " v])
    (seq (:resource_modifiers current-results)))
   
   [:h4.title.is-4 "Virtues"]
   (map-indexed
    (fn [idx [k v]]
      [:p [:span k] " " v])
    (seq (:virtue_modifiers current-results)))
   
   [:h4.title.is-4 "Ancient enemy"]
   [:p (or (:ancient_enemy current-results) "None")]
   
   [:h4.title.is-4 "New enemy"]
   [:p (or (:new_enemy current-results) "None")]
   
   [:h4.title.is-4 "Wyter abilities"]
   (map
    (fn [s]
      [:p [:> ReactMarkdown s]])
    (:wyter_abilities current-results))
   
   [:h4.title.is-4 "Special effects"]
   (map
    (fn [s]
      [:p [:> ReactMarkdown s]])
    (:specials current-results))])