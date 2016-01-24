(ns eponai.client.ui.tag
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.client.ui :refer-macros [opts]]
            [garden.core :refer [css]]
            [sablono.core :refer-macros [html]]))

(defui Tag
  static om/IQuery
  (query [_]
    [:tag/name])
  Object
  (render
    [this]
    (let [{tag-name :tag/name
           :keys [::edit-mode ::delete-fn
                  on-click]} (om/props this)]
      (html
        [:div
         (opts {:class "btn-group btn-group-xs"
                :style {:padding "0.1em"}})

         [:button
          {:class    "btn btn-info"
           :on-click (or on-click #(prn (.. % -target)))}
          tag-name]

         (when edit-mode
           [:button
            {:class    "btn btn-info"
             :on-click #(do (delete-fn) nil)}
            "x"])]))))

(def ->Tag (om/factory Tag {:keyfn :tag/name}))

(defn tag-props
  ([name] (tag-props name nil))
  ([name delete-fn]
   (merge {:tag/name name}
          (when delete-fn
            {::edit-mode true
             ::delete-fn delete-fn}))))
