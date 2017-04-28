(ns eponai.common.ui.store.stream-settings
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.stream :as stream]
    [eponai.client.parser.message :as msg]
    [eponai.client.chat :as client.chat]
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
    [eponai.client.routes :as routes]
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
                                    {:chat.message/user [:user/email {:user/profile [{:user.profile/photo [:photo/path]}]}]}
                                    :chat.message/text
                                    :chat.message/timestamp]}]}
     {:query/auth [:db/id]}])
  chat/ISendChatMessage
  (get-chat-message [this]
    (:chat-message (om/get-state this)))
  (reset-chat-message! [this]
    (om/update-state! this assoc :chat-message ""))
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
      (dom/div
        {:id "sulo-stream-settings"}
        (grid/row-column
          nil
          (callout/callout
            (css/add-class :stream-status)
            (grid/row
              (css/align :middle)
              (grid/column
                (css/add-class :shrink)
                (cond (or (nil? stream-state)
                          (= :stream.state/offline stream-state))
                      (dom/i {:classes ["status offline fa fa-circle fa-fw"]})
                      (= stream-state :stream.state/online)
                      (dom/i {:classes ["status online fa fa-check-circle fa-fw"]})
                      (= stream-state :stream.state/live)
                      (dom/i {:classes ["status live fa fa-wifi fa-fw"]})))
              (grid/column
                nil
                (dom/span nil "Your stream is ")
                (if (or (nil? stream-state)
                        (= :stream.state/offline stream-state))
                  (dom/span {:classes ["status" "offline"]} "Offline")
                  (dom/span
                    (cond->> {:classes ["status"]}
                             (= stream-state :stream.state/online)
                             (css/add-class :online)
                             (= stream-state :stream.state/live)
                             (css/add-class :live))
                    (condp = stream-state
                      :stream.state/online "Online"
                      :stream.state/live "Live"))))
              (grid/column
                (css/text-align :right)
                (condp = stream-state
                  :stream.state/online
                  (dom/a
                    (->> (css/button {:onClick #(om/transact! this [(list 'stream/go-live {:store-id (:db/id store)}) :query/stream])})
                         (css/add-class :highlight))
                    (dom/strong nil "Go live!"))
                  :stream.state/offline
                  (dom/a
                    (css/button-hollow {:onClick #(binding [parser/*parser-allow-local-read* false]
                                                   (om/transact! this [{:query/stream [:stream/state]}]))})
                    (dom/i {:classes ["fa fa-refresh fa-fw"]})
                    (dom/strong nil "Refresh"))
                  :stream.state/live
                  (dom/a
                    (css/button-hollow)
                    (dom/strong nil "Stop streaming"))))))

          (callout/callout
            nil
            (grid/row
              (css/align :center)
              (grid/column
                (->> (grid/column-size {:small 12 :large 8}))
                (stream/->Stream (om/computed (:proxy/stream props)
                                              {:hide-chat? true
                                               :store      store})))
              (grid/column
                (->> (grid/column-size {:small 12 :medium 8 :large 4})
                     (css/add-class :chat-container))
                (callout/header nil "Live Chat")
                (dom/div
                  (css/add-class :chat-content)
                  (dom/div
                    (css/add-class :chat-messages)
                    (elements/message-list (chat/get-messages this)))
                  (dom/div
                    (css/add-class :chat-input)
                    (grid/row
                      (css/align :middle)
                      (grid/column
                        nil
                        (dom/input
                          {:type        "text"
                           :placeholder "Say someting..."
                           :value       (or chat-message "")
                           :onKeyDown   #?(:cljs
                                                #(when (utils/enter-pressed? %)
                                                  (chat/send-message this))
                                           :clj identity)
                           :onChange    #(om/update-state! this assoc :chat-message (.-value (.-target %)))}))
                      (grid/column
                        (css/add-class :shrink)
                        (dom/a
                          (->> {:onClick #(chat/send-message this)}
                               (css/button-hollow)
                               (css/add-class :primary))
                          (dom/i {:classes ["fa fa-send-o fa-fw"]})))))))))
          (grid/row
            nil
            (grid/column
              (grid/column-size {:small 12 :large 8})
              (grid/row
                nil
                (grid/column
                  nil
                  (callout/callout
                    nil
                    (callout/header nil "Encoder Setup")
                    (grid/row
                      (css/align :bottom)
                      (grid/column
                        (grid/column-size {:small 12 :medium 8})
                        (dom/label nil "Server URL")
                        (dom/input {:type  "text"
                                    :value (or (:ui.singleton.stream-config/publisher-url stream-config) "")}))
                      (grid/column
                        nil
                        (dom/a
                          (css/button-hollow)
                          (dom/span nil "Copy URL"))))
                    (grid/row
                      (css/align :bottom)
                      (grid/column
                        (grid/column-size {:small 12 :medium 8})
                        (dom/label nil "Stream Key")
                        (dom/input {:type        "text"
                                    :value       (if (msg/final? message)
                                                   (:token (msg/message message))
                                                   "")
                                    ;:defaultValue (if (msg/final? message)
                                    ;                (:token (msg/message message))
                                    ;                "")
                                    :placeholder "Click below to generate new token"}))
                      (grid/column
                        nil
                        (dom/a
                          (css/button-hollow {:onClick #(msg/om-transact! this `[(stream-token/generate ~{:store-id (:db/id store)})])})
                          (dom/span nil "Generate Key")))))))
              (grid/row
                nil
                (grid/column
                  nil
                  (callout/callout
                    nil
                    (callout/header nil "Analytics")
                    (dom/table nil
                               (dom/thead nil
                                          (dom/tr nil
                                                  (dom/th nil (dom/span nil "Viewers"))
                                                  (dom/th nil (dom/span nil "Messages/min"))))
                               (dom/tbody nil
                                          (dom/tr nil
                                                  (dom/td nil (dom/span
                                                                (css/add-class :stat) "0"))
                                                  (dom/td nil (dom/span (css/add-class :stat) "0")))))))))

            (grid/column
              nil
              (callout/callout
                (css/add-class :setup-checklist)
                (callout/header nil "Setup checklist")
                (dom/ul
                  nil
                  ;; TODO write up some real checklist points and link to relevante resources @diana
                  (menu/item
                    nil
                    (dom/p nil "Setup encoding software")
                    (dom/p nil
                           (dom/small nil "Before you can start streaming on SULO Live, you need to download encoding software, and then set it up. Learn more about setting up encoders in our ")
                           (dom/a {:href (routes/url :help/encoding)
                                   :target "_blank"} (dom/small nil "Guide to Encoding"))
                           (dom/small nil ". You'll need to use the Server URL and Stream key to configure the encoding software.")))

                  ;"Before you can start streaming on YouTube, you need to download encoding software, and then set it up. Learn about Live Verified encoders in our Guide to Encoding.\nYou may need to use the server URL and stream name / key below to configure the encoding software."
                  ;(menu/item
                  ;  nil
                  ;  (dom/p nil "Add stream info")
                  ;  (dom/p nil (dom/small nil "Enter an interesting title and description, and then upload a thumbnail image.\nIf you're live streaming a video game, include the game title to help new fans find you.")))
                  (menu/item
                    nil
                    (dom/p nil "Publish stream")
                    (dom/p nil (dom/small nil "To start streaming, start your encoder. Hit Refresh and the status bar will indicate when your streaming content is being published to our servers. At this point you'll be able to see a preview of your stream, but you're not yet visible to others on SULO Live.")))
                  (menu/item
                    nil
                    (dom/p nil "Go live")
                    (dom/p nil (dom/small nil "To go live and hangout with your crowd, click 'Go live' and your stream will be visible on your store page. To stop streaming, stop your encoder. Ready to go live again? Any time you send content, hit 'Go live' and you're live!"))))))))))))

(def ->StreamSettings (om/factory StreamSettings))