(ns eponai.common.ui.store.stream-settings
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.stream :as stream]
    [eponai.client.parser.message :as msg]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.css :as css]))

(defui StreamSettings
  static om/IQuery
  (query [_]
    [:query/messages
     {:proxy/stream (om/get-query stream/Stream)}])
  Object
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          {:proxy/keys [stream]} (om/props this)
          message (msg/last-message this 'stream-token/generate)]
      (debug ["STREAM_STORE_MESSAGE:  " message :component-state (om/get-state this)])
      (my-dom/div
        {:id "sulo-stream-settings"}
        ;(my-dom/div
        ;  (css/grid-row)
        ;  (my-dom/div
        ;    (css/grid-column)
        ;    (dom/h3 nil "Streaming")))

        (my-dom/div
          (css/grid-row)
          (my-dom/div
            (css/grid-column)
            (my-dom/div
              (->> (css/add-class ::css/callout)
                   (css/add-class ::css/primary))
              (my-dom/div
                (->> (css/grid-row)
                     (css/align :middle))
                (my-dom/div
                  (->> (css/grid-column)
                       (css/add-class :shrink)
                       (css/add-class :stream-status-container))
                  (dom/span #js {:className "badge alert"} "off")
                  (dom/span #js {:className "badge success"} "on")
                  (dom/h3 nil "Offline"))
                (my-dom/div
                  (->> (css/grid-column)
                       (css/text-align :center))
                  (dom/h4 nil "Welcome"))))

            (my-dom/div
              (css/add-class ::css/callout)
              (my-dom/div
                (css/grid-row)
                ;(my-dom/div
                ;  (->> (css/grid-column)
                ;       (css/grid-column-size {:small 12 :medium 3})))
                (my-dom/div
                  (css/grid-column)
                  (dom/h4 nil "Stream preview")
                  (stream/->Stream (om/computed stream
                                                {:hide-chat? true})))
                (my-dom/div
                  (->> (css/grid-column)
                       (css/grid-column-size {:small 12 :medium 4})
                       (css/add-class :chat-container))
                  (dom/h4 nil "Live Chat")
                  (dom/div #js {:className "chat-content"}
                            (dom/div #js {:className "chat-messages"})
                            (dom/input #js {:type "text"}))))
              ;(dom/div  #js {:className "flex-video widescreen"}
              ;  (dom/video nil ))
              )
            (my-dom/div
              (css/add-class ::css/callout)
              (my-dom/div
                (->> (css/grid-row)
                     (css/align :bottom))
                (my-dom/div
                  (css/grid-column)
                  (dom/h4 nil "Stream Key")
                  (dom/input #js {:type        "text"
                                  :value       (if (msg/final? message)
                                                 (:token (msg/message message))
                                                 "")
                                  ;:defaultValue (if (msg/final? message)
                                  ;                (:token (msg/message message))
                                  ;                "")
                                  :placeholder "Click below to generate new token"}))
                (my-dom/div
                  (->> (css/grid-column)
                       (css/text-align :right)
                       (css/add-class :shrink))
                  (dom/a #js {:className "button"
                              :onClick   #(msg/om-transact! this `[(stream-token/generate ~{:store-id (:db/id store)})])}
                         (dom/span nil "Generate New Key")))))))))))

(def ->StreamSettings (om/factory StreamSettings))