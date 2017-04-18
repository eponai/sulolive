(ns eponai.common.ui.store.stream-settings
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.stream :as stream]
    [eponai.client.parser.message :as msg]
    [eponai.client.chat :as client.chat]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.chat :as chat]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements :as elements]
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.common.parser :as parser]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.grid :as grid]))

(defn- get-store [component-or-props]
  (get-in (om/get-computed component-or-props) [:store]))

(defn- get-store-id [component-or-props]
  (:db/id (get-store component-or-props)))

(defui StreamSettings
  static om/IQuery
  (query [_]
    [:query/messages
     {:proxy/stream (om/get-query stream/Stream)}
     {:query/stream [:stream/state]}
     {:query/stream-config [:ui.singleton.stream-config/publisher-url]}
     {:query/chat [:chat/store
                   ;; ex chat modes: :chat.mode/public :chat.mode/sub-only :chat.mode/fb-authed :chat.mode/owner-only
                   :chat/modes
                   {:chat/messages [:chat.message/client-side-message?
                                    {:chat.message/user [:user/email {:user/photo [:photo/path]}]}
                                    :chat.message/text
                                    :chat.message/timestamp]}]}
     {:query/auth [:db/id]}])
  chat/ISendChatMessage
  (get-chat-message [this]
    (:chat-message (om/get-state this)))
  (reset-chat-message! [this]
    (om/update-state! this assoc :chat-message ""))
  (is-logged-in? [this]
    (some? (get-in (om/props this) [:query/auth :db/id])))
  ;; This chat store listener is a copy of what's in ui.chat
  client.chat/IStoreChatListener
  (start-listening! [this store-id]
    (client.chat/start-listening! (:shared/store-chat-listener (om/shared this)) store-id))
  (stop-listening! [this store-id]
    (client.chat/stop-listening! (:shared/store-chat-listener (om/shared this)) store-id))
  Object
  (componentWillUnmount [this]
    (client.chat/stop-listening! this (get-store-id this)))
  (componentDidUpdate [this prev-props prev-state]
    (let [old-store (get-store-id prev-props)
          new-store (get-store-id (om/props this))]
      (when (not= old-store new-store)
        (client.chat/stop-listening! this old-store)
        (client.chat/start-listening! this new-store))))
  (componentDidMount [this]
    (client.chat/start-listening! this (get-store-id this)))
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          {:query/keys [stream chat stream-config]
           :as         props} (om/props this)
          stream-state (:stream/state stream)
          chat-message (:chat-message (om/get-state this))
          message (msg/last-message this 'stream-token/generate)]
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
            (callout/callout
              (css/add-class :stream-status)
              (grid/row
                (css/align :middle)
                (grid/column
                  (css/add-class :shrink)
                  (cond (or (nil? stream-state)
                          (= :stream.state/offline stream-state))
                        (my-dom/i {:classes ["status offline fa fa-circle fa-fw"]})
                        (= stream-state :stream.state/online)
                        (my-dom/i {:classes ["status online fa fa-check-circle fa-fw"]})
                        (= stream-state :stream.state/live)
                        (my-dom/i {:classes ["status live fa fa-wifi fa-fw"]})))
                (grid/column
                  nil
                  ;(dom/span #js {:className "badge alert"} "off")
                  ;(dom/span #js {:className "badge success"} "on")
                  (my-dom/span nil "Your stream is ")
                  (if (or (nil? stream-state)
                          (= :stream.state/offline stream-state))
                    (my-dom/span {:classes ["status" "offline"]} "Offline")
                    (my-dom/span
                      (cond->> {:classes ["status"]}
                               (= stream-state :stream.state/online)
                               (css/add-class :online)
                               (= stream-state :stream.state/live)
                               (css/add-class :live))
                      (condp = stream-state
                                   :stream.state/online "Online"
                                   :stream.state/live "Live"))
                    ;(dom/div nil
                    ;  (dom/h3 nil (dom/strong #js {:className "highlight"} "Go Live!")))
                    ))
                (my-dom/div
                  (->> (css/grid-column)
                       (css/text-align :right))
                  (condp = stream-state
                    :stream.state/online
                    (dom/a #js {:onClick   #(om/transact! this [(list 'stream/go-live {:store-id (:db/id store)}) :query/stream])
                                :className "button highlight"}
                           (dom/strong nil "Go Live!"))
                    :stream.state/offline
                    (dom/a #js {:onClick   #(binding [parser/*parser-allow-local-read* false]
                                             (om/transact! this [{:query/stream [:stream/state]}]))
                                :className "button hollow"}
                           (my-dom/i {:classes ["fa fa-refresh fa-fw"]})
                           (my-dom/strong nil "Refresh"))
                    :stream.state/live
                    (dom/a #js {:className "button hollow"}
                           (dom/strong nil "Stop streaming"))))))

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
                  (stream/->Stream (om/computed (:proxy/stream props)
                                                {:hide-chat? true
                                                 :store store}))
                  )
                (my-dom/div
                  (->> (css/grid-column)
                       (css/grid-column-size {:small 12 :medium 8 :large 4})
                       (css/add-class :chat-container))
                  (dom/h4 nil "Live Chat")
                  (dom/div #js {:className "chat-content"}
                            (dom/div #js {:className "chat-messages"}
                              (elements/message-list (chat/get-messages this)))
                            (dom/div #js {:className "chat-input"}
                              (my-dom/div
                                (->> (css/grid-row)
                                     (css/align :middle))
                                (my-dom/div (css/grid-column)
                                            (dom/input #js {:className   ""
                                                            :type        "text"
                                                            :placeholder "Say someting..."
                                                            :value       (or chat-message "")
                                                            :onKeyDown   #?(:cljs
                                                                            #(when (utils/enter-pressed? %)
                                                                               (chat/send-message this))
                                                                            :clj identity)
                                                            :onChange    #(om/update-state! this assoc :chat-message (.-value (.-target %)))}))
                                (my-dom/div (->> (css/grid-column)
                                                 (css/add-class :shrink))
                                            (dom/a #js {:className "button hollow primary"
                                                        :onClick   #(chat/send-message this)}
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
                          (dom/input #js {:type  "text"
                                          :value (or (:ui.singleton.stream-config/publisher-url stream-config) "")}))
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
                          (dom/a #js {:className "button hollow"
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