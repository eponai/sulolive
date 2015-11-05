(ns flipmunks.budget.ui.tag
  (:require [om.next :as om :refer-macros [defui]]
            [flipmunks.budget.ui :refer [style]]
            [garden.core :refer [css]]
            [sablono.core :refer-macros [html]]))

(defui Tag
       static om/IQuery
       (query [this] [:tag/name])
       Object
       (render
         [this]
         (let [{:keys [::edit-mode ::delete-fn] tag-name :tag/name} (om/props this)]
           (html [:div (style {:display "inline-block"})
                 [:style (css [:#tag-body {:display        "inline-block"
                                           :padding        "0.2em 0.2em"
                                           :margin         "0.0em"
                                           :border-width   "1px"
                                           :border-style   "solid"
                                           :font-size      "1em"
                                           :border-color   "#ddd"
                                           :cursor         "default"}
                               [:&:hover {:border-color "#aaa"}]
                               [:&:active {:border-color "#ddd"}]])]
                  [:div#tag-body
                   (style {:border-radius  (if edit-mode "0.3em 0.0em 0.0em 0.3em" "0.3em")})
                   [:span {:on-click #(prn (.. % -target))} tag-name]]
                  (when edit-mode
                    [:span#tag-body (merge (style {:border-radius "0.0em 0.3em 0.3em 0.0em"
                                                   :border-width "1px 1px 1px 0px"})
                                  {:on-click #(do (delete-fn) nil)})
                     "x"])]))))

(def ->Tag (om/factory Tag))

(defn tag-props
  ([name] (tag-props name nil))
  ([name delete-fn]
   (merge {:tag/name name}
          (when delete-fn
            {::edit-mode true
             ::delete-fn delete-fn}))))
