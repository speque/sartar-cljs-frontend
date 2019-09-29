(ns sartar.frontend.components.results
  (:require ["react-markdown" :as ReactMarkdown]
            [sartar.frontend.constants :refer [runes]]))

(defn results-component [current-results]
  [:div.content.has-text-centered
   [:h3 "Results"]
   [:div.columns.has-text-left
    [:div.column
     [:h4 "Runes"]
     (map-indexed
      (fn [idx [k v]]
        [:p
         {:key (str "rune-" k)}
         [:span.runes ((keyword k) runes)] v])
      (seq (:rune_modifiers current-results)))]

    [:div.column
     [:h4 "Resources"]
     (map-indexed
      (fn [idx [k v]]
        [:p.modifier
         {:key (str "resource-" k)}
         [:span k] " " v])
      (seq (:resource_modifiers current-results)))]

    [:div.column
     [:h4 "Virtues"]
     (map-indexed
      (fn [idx [k v]]
        [:p.modifier
         {:key (str "virtue-" k)}
         [:span k] " " v])
      (seq (:virtue_modifiers current-results)))]]

   [:div.columns
    [:div.column
     [:h4 "Ancient enemy"]
     (if (:ancient_enemy current-results)
       [:> ReactMarkdown (:ancient_enemy current-results)]
       [:p "None"])

     [:h4 "New enemy"]
     [:p (or (:new_enemy current-results) "None")]

     [:h4 "Wyter abilities"]
     (map-indexed
      (fn [idx ability]
        [:p
         {:key (str "wyter-ability-" idx)}
         [:> ReactMarkdown {:disallowedTypes ["paragraph"] :unwrapDisallowed true} ability]])
      (:wyter_abilities current-results))]

    [:div.column
     [:h4 "Special effects"]
     (map-indexed
      (fn [idx effect]
        [:p
         {:key (str "special-effect-" idx)}
         [:> ReactMarkdown {:disallowedTypes ["paragraph"] :unwrapDisallowed true} effect]])
      (:specials current-results))]]])