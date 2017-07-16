(ns eponai.common.ui.chat
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements :as elements]
    [eponai.common.shared :as shared]
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.client.chat :as client.chat]
    ;; Require eponai.web.chat so it's required somewhere.
    #?(:cljs [eponai.web.chat])
    [clojure.string :as str]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.mixpanel :as mixpanel]
    [eponai.common.format.date :as date]
    [eponai.web.ui.photo :as photo]
    [eponai.web.ui.button :as button]))

(defn get-messages [component]
  (let [messages (get-in (om/props component) [:query/chat :chat/messages])
        {client-side true server-side false} (group-by (comp true? :chat.message/client-side-message?) messages)
        now (date/current-millis)
        msgs (into []
                   (filter (fn [msg]
                             (let [millis-since-message (- now (:chat.message/created-at msg))]
                               (> client.chat/message-time-limit-ms millis-since-message))))
                   (concat (sort-by :chat.message/created-at server-side) client-side))]
    msgs))

(defn- get-store [component-or-props]
  (get-in (om/get-computed component-or-props) [:store]))

(defn- get-store-id [component-or-props]
  (:db/id (get-store component-or-props)))

;; TODO: This ISendChatMessage stuff should probably be done without a protocol.
(defprotocol IHasChatMessage
  (get-chat-message [this])
  (reset-chat-message! [this]))

(defn send-message [component has-message]
  (let [chat-message (get-chat-message has-message)]
    (reset-chat-message! has-message)
    (when-not (str/blank? chat-message)
      (mixpanel/track "Send chat message" {:message  chat-message
                                           :store-id (get-store-id component)})

      (om/transact! component `[(chat/send-message
                                  ~{:store (select-keys (get-store component) [:db/id])
                                    :text  chat-message})
                                :query/chat]))))

(defui ChatInput
  IHasChatMessage
  (get-chat-message [this]
    (:chat-message (om/get-state this)))
  (reset-chat-message! [this]
    (om/update-state! this assoc :chat-message ""))
  Object
  (render [this]
    (let [{:keys [chat-message]} (om/get-state this)
          {:keys [on-enter]} (om/get-computed this)]
      (dom/div
        (css/add-class :message-input-container)
        (dom/input {:className      ""
                       :type        "text"
                       :placeholder "Say something..."
                       :value       (or chat-message "")
                       :onKeyDown   #?(:cljs #(when (utils/enter-pressed? %)
                                               (on-enter this))
                                       :clj  identity)
                       :maxLength   500
                       :onChange    #(om/update-state! this assoc :chat-message (.-value (.-target %)))})
        (dom/a
          (->> (css/button-hollow {:onClick #(on-enter this)})
               (css/add-class :secondary))
          (dom/i {:classes ["fa fa-send-o fa-fw"]}))))))

(def ->ChatInput (om/factory ChatInput))

(defn toggle-button [component]
  [
   (dom/a
     (->> (css/button {:onClick #(.toggle-chat component true)})
          (css/add-classes [:toggle-button :show-button])
          (css/show-for :medium))
     (dom/span {:classes ["fa fa-comments fa-fw"]}))
   (dom/a
     (->> (css/button-hollow {:onClick #(.toggle-chat component false)})
          (css/add-classes [:secondary :toggle-button :hide-button])
          (css/show-for :medium))
     (dom/i
       (css/add-class "fa fa-chevron-right fa-fw")))

   (dom/a
     (->> (css/button {:onClick #(.toggle-chat-small component true)})
          (css/add-classes [:toggle-button-small :show-button])
          (css/hide-for :medium))
     (dom/span {:classes ["fa fa-comments fa-fw"]}))
   (dom/a
     (->> (css/button-hollow {:onClick #(.toggle-chat-small component false)})
          (css/add-classes [:secondary :toggle-button-small :hide-button])
          (css/hide-for :medium))
     (dom/i
       (css/add-class "fa fa-chevron-right fa-fw")))])

(defn status-message [online-status]
  (cond (number? online-status)
        (let [interval (date/interval online-status (date/current-millis))
              in-hours (date/in-hours interval)
              in-mins (date/in-minutes interval)]
          (cond (>= 0 in-hours)
                (str "active " in-mins " mins ago")
                (>= 24 in-hours)
                (str "active " in-hours " hrs ago")
                :else
                ""))
        (true? online-status)
        "online"
        :else
        "offline"))

(defui StreamChat
  static om/IQuery
  (query [_]
    [{:query/chat client.chat/query-chat-pattern}])
  client.chat/IStoreChatListener
  (start-listening! [this store-id]
    (debug "Will start listening to store-id: " store-id)
    (client.chat/start-listening! (shared/by-key this :shared/store-chat-listener) store-id))
  (stop-listening! [this store-id]
    (debug "Will stop listening to store-id: " store-id)
    (client.chat/stop-listening! (shared/by-key this :shared/store-chat-listener) store-id))
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
      (mixpanel/track "Toggle chat window" {:show show?})
      (when on-toggle-chat
        (on-toggle-chat show?))
      (om/update-state! this assoc :show-chat? show?)))

  (toggle-chat-small [this show?]
    (let [{:keys [on-toggle-chat]} (om/get-computed this)]
      (mixpanel/track "Toggle chat window" {:show show?})
      (debug "Toggle chat: " show?)
      (when on-toggle-chat
        (on-toggle-chat show?))
      (om/update-state! this assoc :show-chat-small? show?)))
  (initLocalState [this]
    (let [{:keys [show?]} (om/get-computed this)]
      {:show-chat? show?}))
  (render [this]
    (let [{:keys [show-chat? show-chat-small?]} (om/get-state this)
          {:keys [stream-overlay? store store-online-status visitor-count current-route]} (om/get-computed this)
          store-name (get-in store [:store/profile :store.profile/name])
          welcome-msg (if (and (number? visitor-count) (< 1 visitor-count))
                        (if (= (:route current-route) :store-dashboard/stream)
                          (str "Welcome to your public chat room! You have visitors in your store right now, try and say hi")
                          (str "Welcome to " store-name " public chatroom! Other users are in this store, try to say hi"))
                        (if (= (:route current-route) :store-dashboard/stream)
                          (str "Welcome to your public chat room! Your customers can use the chat to communicate with you")
                          (str "Welcome to " store-name " public chatroom! Use the chat to hangout with " store-name " and other users")))
          messages (or (not-empty (get-messages this)) [{:chat.message/user :automatic :chat.message/text welcome-msg}])
          status-msg (status-message store-online-status)]
      (debug "Chat messages: " messages)
      (debug "Store online: " store-online-status)
      (dom/div
        (cond->> (css/add-class :chat-container)
                 show-chat?
                 (css/add-class :show)
                 show-chat-small?
                 (css/add-class :show-small)
                 stream-overlay?
                 (css/add-class :stream-chat-container))


        (dom/div
          (cond->> (css/add-class :chat-menu)
                   (= true store-online-status)
                   (css/add-class :is-online)
                   (not= status-msg "offline")
                   (css/add-class :just-online))

          (dom/div
            (css/add-classes [:chat-controls])

            (toggle-button this)
            (dom/p (css/add-class :title) (dom/span nil "Public live chat")))

          (dom/div
            (css/add-class :chat-info)
            (dom/div
              (css/add-classes [:sl-tooltip :chat-visitors])
              (dom/i {:classes ["fa fa-user fa-fw"]})
              (dom/span nil (str visitor-count))
              (dom/span (css/add-class :sl-tooltip-text) (if (= 1 visitor-count)
                                                              (str visitor-count " visitor is in this store right now")
                                                              (str visitor-count " visitors are in this store right now"))))
            (dom/div
              (css/add-classes [:chat-status])
              (dom/p (css/add-class :sl-tooltip) (dom/span nil status-msg)
                     (dom/span (css/add-class :sl-tooltip-text)
                               (str (get-in store [:store/profile :store.profile/name]) " is " (if (= true store-online-status) "online" "offline") " right now")))
              (photo/store-photo store {:transformation :transformation/thumbnail-tiny})
              )))


        (dom/div
          (css/add-class :chat-content {:id "sl-chat-content"})
          (elements/message-list messages))
        (->ChatInput (om/computed {}
                                  {:on-enter (fn [chat-input]
                                               (send-message this chat-input))}))
        ;(my-dom/div
        ;  (css/add-classes [ :visitor-count])
        ;  (my-dom/p (css/add-class :sl-tooltip)
        ;            (my-dom/i {:classes ["fa fa-user fa-fw"]}) (my-dom/span nil (str visitor-count))
        ;            (my-dom/span (css/add-classes [:top :sl-tooltip-text]) "Visitors in store right now")))
        ))))

(def ->StreamChat (om/factory StreamChat))