(ns eponai.mobile.components.table-view
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [list-view]]
    [eponai.web.ui.utils :as web.utils]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [debug]]))

(defui TableView
  web.utils/ISyncStateWithProps
  (props->init-state [_ props]
    (let [{:keys [rows]} props
          {:keys [row-has-changed]} (:om.next/computed props)
          ds (js/ReactNative.ListView.DataSource.
               #js {:rowHasChanged (fn [prev next]
                                     (if (fn? row-has-changed)
                                       (row-has-changed prev next)
                                       (not= prev next)))})]
      {:ds (if (seq rows)
             ;; Wraps the rows in a function to not destroy the cljs data.
             (.cloneWithRows ds (into-array (map constantly rows)))
             ds)}))
  Object
  (initLocalState [this]
    (web.utils/props->init-state this (om/props this)))
  (componentWillReceiveProps [this next-props]
    (web.utils/sync-with-received-props this next-props))

  (render [this]
    (let [{:keys [ds]} (om/get-state this)
          {:keys [render-row style]} (om/get-computed this)]
      (list-view
        (opts {:dataSource                       ds
               :automaticallyAdjustContentInsets false
               :renderRow                        (fn [r]
                                                   {:pre [(fn? r)]}
                                                   (when (fn? render-row)
                                                     (render-row (r))))
               :style                            style})))))

(def ->TableView (om/factory TableView))