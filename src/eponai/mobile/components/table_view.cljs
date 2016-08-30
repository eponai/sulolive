(ns eponai.mobile.components.table-view
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [list-view]]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [debug]]))

(defui TableView
  Object
  (initLocalState [this]
    (let [{:keys [rows]} (om/props this)
          {:keys [row-has-changed]} (om/get-computed this)
          ds (js/ReactNative.ListView.DataSource.
               #js {:rowHasChanged (fn [prev next]
                                     (if (fn? row-has-changed)
                                       (row-has-changed prev next)
                                       (not= prev next)))})]
      {:ds (if (seq rows)
             (.cloneWithRows ds rows)
             ds)}))

  (render [this]
    (let [{:keys [ds]} (om/get-state this)
          {:keys [render-row]} (om/get-computed this)]
      (list-view
        (opts {:dataSource                       ds
               :automaticallyAdjustContentInsets false
               :renderRow                        (fn [r]
                                                   (when (fn? render-row)
                                                     (render-row r)))})))))

(def ->TableView (om/factory TableView))