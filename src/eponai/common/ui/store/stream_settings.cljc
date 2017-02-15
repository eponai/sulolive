(ns eponai.common.ui.store.stream-settings
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.client.parser.message :as msg]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.css :as css]))

(defui StreamSettings
  static om/IQuery
  (query [_]
    [:query/messages])
  Object
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          message (msg/last-message this 'stream-token/generate)]
      (debug ["STREAM_STORE_MESSAGE:  " message :component-state (om/get-state this)])
      (my-dom/div
        (->> (css/grid-row)
             css/grid-column)
        (my-dom/div
          (->> (css/grid-row)
               (css/align :bottom))
          (my-dom/div (css/grid-column)
                      (dom/label nil "Stream Key")
                      (dom/input #js {:type        "text"
                                      :value       (when (msg/final? message)
                                                     (:token (msg/message message))
                                                     )
                                      :placeholder "Click below to generate new token"}))
          (my-dom/div
            (->> (css/grid-column)
                 (css/text-align :right)
                 (css/add-class :shrink))
            (dom/a #js {:className "button"
                        :onClick   #(msg/om-transact! this `[(stream-token/generate ~{:store-id (:db/id store)})])}
                   (dom/strong nil "Generate New Key"))))))))

(def ->StreamSettings (om/factory StreamSettings))