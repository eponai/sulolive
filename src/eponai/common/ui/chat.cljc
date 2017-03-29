(ns eponai.common.ui.chat
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements :as elements]
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.client.chat :as client.chat]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(defn enter-pressed? [e]
  (= 13 (.-keyCode e)))

(defn get-messages [component]
  (let [messages (get-in (om/props component) [:query/chat :chat/messages])
        {client-side true server-side false} (group-by (comp true? :chat.message/client-side-message?) messages)]
    (concat server-side client-side)))

(defn- get-store [component-or-props]
  (get-in (om/get-computed component-or-props) [:store]))

(defn- get-store-id [component-or-props]
  (:db/id (get-store component-or-props)))

(defui StreamChat
  static om/IQuery
  (query [_]
    [{:query/chat [:chat/store
                   ;; ex chat modes: :chat.mode/public :chat.mode/sub-only :chat.mode/fb-authed :chat.mode/owner-only
                   :chat/modes
                   {:chat/messages [:chat.message/client-side-message?
                                    {:chat.message/user [:user/email {:user/photo [:photo/path]}]}
                                    :chat.message/text
                                    :chat.message/timestamp]}]}
     {:query/auth [:db/id]}])
  client.chat/IStoreChatListener
  (start-listening! [this store-id]
    (debug "Will start listening to store-id: " store-id)
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
  (toggle-chat [this show?]
    (let [{:keys [on-toggle-chat]} (om/get-computed this)]
      (when on-toggle-chat
        (on-toggle-chat show?))
      (om/update-state! this assoc :show-chat? show?)))
  (initLocalState [this]
    (let [{:keys [show?]} (om/get-computed this)]
      {:show-chat? show?}))
  (send-message [this]
    (let [{:keys [chat-message]} (om/get-state this)]
      (when (seq chat-message)
        (if (get-in (om/props this) [:query/auth :db/id])
          (do (om/transact! this `[(chat/send-message
                                     ~{:store (select-keys (get-store this) [:db/id])
                                       :text  chat-message})
                                   :query/chat])
              (om/update-state! this assoc :chat-message ""))
          #?(:cljs (js/alert "Log in to send chat messages"))))))
  (render [this]
    (let [{:keys [show-chat? chat-message]} (om/get-state this)
          messages (get-messages this)]
      (my-dom/div
        (cond->> (css/add-class ::css/stream-chat-container (css/add-class :chat-container))
                 show-chat?
                 (css/add-class :show))
        (dom/a #js {:className "button show-button"
                    :onClick   #(.toggle-chat this true)}
               (dom/i #js {:className "fa fa-comments fa-fw"}))
        (my-dom/div
          nil
          (dom/a #js {:className "button hollow secondary hide-button"
                      :onClick   #(.toggle-chat this false)}
                 (dom/i #js {:className "fa fa-chevron-right fa-fw"})))

        ;(menu/horizontal nil
        ;                 (menu/item nil
        ;                            (dom/a nil "Chat")))
        (dom/div #js {:className "content"}
          (elements/message-list messages)
          (dom/div #js {:className "input-container"}
            (my-dom/div
              (css/grid-row)
              (my-dom/div (css/grid-column)
                          (dom/input #js {:className   ""
                                          :type        "text"
                                          :placeholder "Say something..."
                                          :value       (or chat-message "")
                                          :onKeyDown   #(when (enter-pressed? %)
                                                          (.send-message this))
                                          :onChange    #(om/update-state! this assoc :chat-message (.-value (.-target %)))}))
              (my-dom/div (->> (css/grid-column)
                               (css/add-class :shrink))
                          (dom/a #js {:className "button green small"
                                      :onClick   #(.send-message this)}
                                 (dom/span nil "Send"))))))
        ))))

(def ->StreamChat (om/factory StreamChat))