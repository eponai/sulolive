(ns eponai.common.ui.store.stream-settings
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.stream :as stream]
    [eponai.client.parser.message :as msg]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.chat :as chat]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements :as elements]))

(defui StreamSettings
  static om/IQuery
  (query [_]
    [:query/messages
     {:proxy/stream (om/get-query stream/Stream)}
     {:query/chat [:chat/store
                   ;; ex chat modes: :chat.mode/public :chat.mode/sub-only :chat.mode/fb-authed :chat.mode/owner-only
                   :chat/modes
                   {:chat/messages [:chat.message/client-side-message?
                                    {:chat.message/user [:user/email {:user/photo [:photo/path]}]}
                                    :chat.message/text
                                    :chat.message/timestamp]}]}])
  Object
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          {:proxy/keys [stream]
           :query/keys [chat]} (om/props this)
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
                (->> (css/grid-row)
                     (css/align :center))
                ;(my-dom/div
                ;  (->> (css/grid-column)
                ;       (css/grid-column-size {:small 12 :medium 3})))
                (my-dom/div
                  (->> (css/grid-column)
                       (css/grid-column-size {:small 12 :large 8}))
                  ;(dom/h4 nil "Stream preview")
                  (stream/->Stream (om/computed stream
                                                {:hide-chat? true
                                                 :store store
                                                 :on-video-load (fn [status] (debug "Video status: " status))})))
                (my-dom/div
                  (->> (css/grid-column)
                       (css/grid-column-size {:small 12 :medium 8 :large 4})
                       (css/add-class :chat-container))
                  (dom/h4 nil "Live Chat")
                  (dom/div #js {:className "chat-content"}
                            (dom/div #js {:className "chat-messages"}
                              (elements/message-list (:chat/messages chat)))
                            (dom/div #js {:className "chat-input"}
                              (my-dom/div
                                (->> (css/grid-row)
                                     (css/align :middle))
                                (my-dom/div (css/grid-column)
                                            (dom/input #js {:className   ""
                                                            :type        "text"
                                                            :placeholder "Say someting..."
                                                            :onChange    #(om/update-state! this assoc :chat-message (.-value (.-target %)))}))
                                (my-dom/div (->> (css/grid-column)
                                                 (css/add-class :shrink))
                                            (dom/a #js {:className "button hollow primary large"}
                                                   (dom/i #js {:className "fa fa-send-o fa-fw"})))))
                            ;(dom/input #js {:type "text"
                            ;                :placeholder "Say something..."})
                            )
                  ))
              ;(dom/div  #js {:className "flex-video widescreen"}
              ;  (dom/video nil ))
              )
            (my-dom/div
              (css/grid-row)
              (my-dom/div
                (->> (css/grid-column)
                     (css/grid-column-size {:small 12 :large 8}))
                (my-dom/div
                  (css/grid-row)
                  (my-dom/div
                    (css/grid-column)
                    (my-dom/div
                      (css/add-class ::css/callout)
                      (dom/h4 nil "Encoder Setup")
                      (my-dom/div
                        (->> (css/grid-row)
                             (css/align :bottom))
                        (my-dom/div
                          (->> (css/grid-column)
                               (css/grid-column-size {:small 12 :medium 8}))
                          (dom/label nil "Server URL")
                          ; TODO fetch the real server URL from somewhere @petter
                          (dom/input #js {:type  "text"
                                          :value "rtmp://rtmp.sulo.live:1935/live"}))
                        (my-dom/div
                          (->> (css/grid-column))
                          (dom/a #js {:className "button hollow"}
                                 (dom/span nil "Copy URL"))))
                      (my-dom/div
                        (->> (css/grid-row)
                             (css/align :bottom))
                        (my-dom/div
                          (->> (css/grid-column)
                               (css/grid-column-size {:small 12 :medium 8}))
                          (dom/label nil "Stream Key")
                          (dom/input #js {:type        "text"
                                          :value       (if (msg/final? message)
                                                         (:token (msg/message message))
                                                         "")
                                          ;:defaultValue (if (msg/final? message)
                                          ;                (:token (msg/message message))
                                          ;                "")
                                          :placeholder "Click below to generate new token"}))
                        (my-dom/div
                          (->> (css/grid-column))
                          (dom/a #js {:className "button"
                                      :onClick   #(msg/om-transact! this `[(stream-token/generate ~{:store-id (:db/id store)})])}
                                 (dom/span nil "Generate Key")))))))
                (my-dom/div
                  (css/grid-row)
                  (my-dom/div
                    (css/grid-column)
                    (my-dom/div
                      (css/add-class ::css/callout)
                      (dom/h4 nil "Analytics")
                      (dom/table nil
                        (dom/thead nil
                                   (dom/tr nil
                                           (dom/th nil (dom/span nil "Viewers"))
                                           (dom/th nil (dom/span nil "Messages/min"))))
                                 (dom/tbody nil
                                            (dom/tr nil
                                                    (dom/td nil (dom/span #js {:className "stat"} "0"))
                                                    (dom/td nil (dom/span #js {:className "stat"} "0")))))))))

              (my-dom/div
                (css/grid-column)
                (my-dom/div
                  (css/add-class ::css/callout)
                  (dom/h4 nil "Checklist")
                  (menu/vertical
                    nil
                    ;; TODO write up some real checklist points and link to relevante resources @diana
                    (menu/item
                      nil (dom/div nil (dom/h6 nil "Setup encoding software")
                                       (dom/p nil (dom/small nil "Before you can start streaming on YouTube, you need to download encoding software, and then set it up. Learn about Live Verified encoders in our Guide to Encoding.\nYou may need to use the server URL and stream name / key below to configure the encoding software."))))
                    (menu/item
                      nil (dom/div nil
                            (dom/h6 nil "Add stream info")
                            (dom/p nil (dom/small nil "Enter an interesting title and description, and then upload a thumbnail image.\nIf you're live streaming a video game, include the game title to help new fans find you."))))
                    (menu/item
                      nil (dom/div nil (dom/h6 nil "Go live")
                                       (dom/p nil (dom/small nil "To start streaming, start your encoder. The status bar will indicate when you're live.\nTo stop streaming, stop your encoder.\nWhen your stream is complete, a public video will be automatically created and uploaded for your fans to view later. Learn more.\nReady to go live again? Any time you send content, you're live!"))))))))))))))

(def ->StreamSettings (om/factory StreamSettings))