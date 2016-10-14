(ns eponai.web.ui.select
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defui Select
  Object
  (initLocalState [this]
    {:is-visible? false
     :hide-fn #(.hide this)})
  (select [this item]
    (let [{:keys [on-select]} (om/get-computed this)]
      (om/update-state! this assoc :selected item)
      (when on-select
        (on-select item))))

  (show [this]
    (let [{:keys [hide-fn]} (om/get-state this)]
      (om/update-state! this assoc :is-visible? true)
      (.. js/document (addEventListener "click" hide-fn))))

  (hide [this]
    (let [{:keys [hide-fn]} (om/get-state this)]
      (debug "Hide")
      (.. js/document (removeEventListener "click" hide-fn))
      (om/update-state! this assoc :is-visible? false)))

  (render [this]
    (let [{:keys [is-visible? selected]} (om/get-state this)
          {:keys [items default]} (om/props this)]
      (html
        [:div.select-container
         {:class (when is-visible? "show")}
         [:div.select-display
          {:class (str (when is-visible? "clicked"))
           :on-click #(.show this)}
          [:span
           (or (:name selected) (:name default))]
          [:i.fa.fa-caret-down.fa-fw]
          ]
         [:div.select-list
          (map (fn [item]
                 [:div.select-item
                  {:on-click #(.select this item)}
                  [:span (:name item)]])
               items)]]))))

(def ->Select (om/factory Select))