(ns eponai.common.ui.chat
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements :as elements]
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.client.chat :as client.chat]
    [clojure.string :as str]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]))

(defn get-messages [component]
  (let [messages (get-in (om/props component) [:query/chat :chat/messages])
        {client-side true server-side false} (group-by (comp true? :chat.message/client-side-message?) messages)]
    (concat server-side client-side)))

(defn- get-store [component-or-props]
  (get-in (om/get-computed component-or-props) [:store]))

(defn- get-store-id [component-or-props]
  (:db/id (get-store component-or-props)))

;; TODO: This ISendChatMessage stuff should probably be done without a protocol.
(defprotocol ISendChatMessage
  (get-chat-message [this])
  (reset-chat-message! [this]))

(defn send-message [component]
  (let [chat-message (get-chat-message component)]
    (when-not (str/blank? chat-message)
      (om/transact! component `[(chat/send-message
                                  ~{:store (select-keys (get-store component) [:db/id])
                                    :text  chat-message})
                                :query/chat])))
  (reset-chat-message! component))

(defui StreamChat
  static om/IQuery
  (query [_]
    [{:query/chat [:chat/store
                   ;; ex chat modes: :chat.mode/public :chat.mode/sub-only :chat.mode/fb-authed :chat.mode/owner-only
                   :chat/modes
                   {:chat/messages [:chat.message/client-side-message?
                                    {:chat.message/user [:user/email {:user/profile [{:user.profile/photo [:photo/id]}]}]}
                                    :chat.message/text
                                    :chat.message/timestamp]}]}])
  client.chat/IStoreChatListener
  (start-listening! [this store-id]
    (debug "Will start listening to store-id: " store-id)
    (client.chat/start-listening! (:shared/store-chat-listener (om/shared this)) store-id))
  (stop-listening! [this store-id]
    (client.chat/stop-listening! (:shared/store-chat-listener (om/shared this)) store-id))
  ISendChatMessage
  (get-chat-message [this]
    (:chat-message (om/get-state this)))
  (reset-chat-message! [this]
    (om/update-state! this assoc :chat-message ""))
  Object
  (componentWillUnmount [this]
    (client.chat/stop-listening! this (get-store-id this)))
  (componentDidUpdate [this prev-props prev-state]
    (let [old-store (get-store-id prev-props)
          new-store (get-store-id (om/props this))]
      (when (not= old-store new-store)
        (client.chat/stop-listening! this old-store)
        (client.chat/start-listening! this new-store))
      (when (not= (:query/chat prev-props) (:query/chat (om/props this)))
        #?(:cljs
           (let [chat-content (utils/element-by-id "sl-chat-message-list")]
             (when chat-content
               (set! (.-scrollTop chat-content) (.-scrollHeight chat-content))))))))
  (componentDidMount [this]
    (client.chat/start-listening! this (get-store-id this))
    #?(:cljs
       (let [chat-content (utils/element-by-id "sl-chat-message-list")]
         (when chat-content
           (set! (.-scrollTop chat-content) (.-scrollHeight chat-content))))))
  (toggle-chat [this show?]
    (let [{:keys [on-toggle-chat]} (om/get-computed this)]
      (when on-toggle-chat
        (on-toggle-chat show?))
      (om/update-state! this assoc :show-chat? show?)))
  (initLocalState [this]
    (let [{:keys [show?]} (om/get-computed this)]
      {:show-chat? show?}))
  (render [this]
    (let [{:keys [show-chat? chat-message]} (om/get-state this)
          messages (get-messages this)
          {:keys [stream-overlay?]} (om/get-computed this)]
      (my-dom/div
        (cond->> (css/add-class :chat-container)
                 show-chat?
                 (css/add-class :show)
                 stream-overlay?
                 (css/add-class :stream-chat-container))
        (menu/horizontal
          nil
          (menu/item
            nil
            (my-dom/a
              (->> (css/button {:onClick   #(.toggle-chat this true)})
                   (css/add-classes [:toggle-button :show-button]))
              (my-dom/i {:classes ["fa fa-comments fa-fw"]})))
          (menu/item
            nil
            (my-dom/a
              (->> (css/button-hollow {:onClick #(.toggle-chat this false)})
                   (css/add-classes [:secondary :toggle-button :hide-button]))
              (my-dom/i
                (css/add-class "fa fa-chevron-right fa-fw")))))

        (my-dom/div
          (css/add-class :chat-content {:id "sl-chat-content"})
          (elements/message-list messages))
        (my-dom/div
          (css/add-class :message-input-container)
          (my-dom/input {:className   ""
                         :type        "text"
                         :placeholder "Say something..."
                         :value       (or chat-message "")
                         :onKeyDown   #?(:cljs #(when (utils/enter-pressed? %)
                                                 (send-message this))
                                         :clj  identity)
                         :maxLength   150
                         :onChange    #(om/update-state! this assoc :chat-message (.-value (.-target %)))})
          (my-dom/a
            (->> (css/button-hollow {:onClick #(send-message this)})
                 (css/add-class :secondary))
            (my-dom/i {:classes ["fa fa-send-o fa-fw"]})))
        ))))

(def ->StreamChat (om/factory StreamChat))