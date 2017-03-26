(ns eponai.common.ui.chat
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.css :as css]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(def fake-messages [{:photo "/assets/img/kids-new.jpg"
                     :text  "this is some message"
                     :user  "Seeley B"
                     }
                    {:photo "/assets/img/men-new.jpg"
                     :text  "Hey there I was wondering something"
                     :user  "Rick"
                     }
                    {:photo "/assets/img/women-new.jpg"
                     :user  "Diana Gren"
                     :text  "Oh yeah mee too, I was wondering how really long messages would show up in the chat list. I mean it could look really really ugly worst case..."}
                    ])

(def fake-photos (mapv :photo fake-messages))

(defn get-messages [component]
  (let [messages (get-in (om/props component) [:query/chat :chat/messages])
        {client-side true server-side false} (group-by (comp true? :chat.message/client-side-message?) messages)]
    (concat server-side client-side)))

(defui StreamChat
  static om/IQuery
  (query [_]
    [{:query/chat [:chat/store
                   ;; ex chat modes: :chat.mode/public :chat.mode/sub-only :chat.mode/fb-authed :chat.mode/owner-only
                   :chat/modes
                   {:chat/messages [:chat.message/client-side-message?
                                    {:chat.message/user [:user/email]}
                                    :chat.message/text
                                    :chat.message/timestamp]}]}
     {:query/auth [:db/id]}])
  Object
  #?(:cljs
     (toggle-chat
       [this show?]
       (let [{:keys [on-toggle-chat]} (om/get-computed this)]
         (when on-toggle-chat
           (on-toggle-chat show?))
         (om/update-state! this assoc :show-chat? show?))))
  (initLocalState [this]
    (let [{:keys [show?]} (om/get-computed this)]
      {:show-chat? show?}))
  (render [this]
    (let [{:keys [show-chat? chat-message]} (om/get-state this)
          {:keys [store]} (om/get-computed this)
          messages (get-messages this)]
      (my-dom/div
        (cond->> (css/add-class ::css/stream-chat-container)
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

        (menu/horizontal nil
                         (menu/item nil
                                    (dom/a nil "Chat")))
        (dom/div #js {:className "content"}
          (menu/vertical
            #?(:cljs
                    (css/add-class :messages-list {:onMouseOver #(set! (.. (utils/element-by-id "the-sulo-app") -style -overflow-y) "hidden")
                                                   :onMouseOut  #(set! (.. (utils/element-by-id "the-sulo-app") -style -overflow-y) "scroll")})
               :clj (css/add-class :messages-list))
            (map (fn [msg]
                   (debug "MSG " msg)
                   (menu/item (css/add-class :message-container)
                              (my-dom/div
                                (->> (css/grid-row)
                                     (css/align :top))
                                (my-dom/div (->> (css/grid-column)
                                                 (css/grid-column-size {:small 2}))
                                            (photo/circle {:src (nth fake-photos
                                                                     (dec (mod (:chat.message/user msg)
                                                                               (count fake-photos)))
                                                                     (first fake-photos))}))
                                (my-dom/div (css/grid-column)
                                            (dom/small nil
                                                       (dom/strong nil (str (get-in msg [:chat.message/user :user/email])
                                                                            ": "))
                                                       (dom/span nil (:chat.message/text msg)))))))
                 messages))
          (dom/div #js {:className "input-container"}
            (my-dom/div
              (css/grid-row)
              (my-dom/div (css/grid-column)
                          (dom/input #js {:className   ""
                                          :type        "text"
                                          :placeholder "Your message..."
                                          :value       (or chat-message "")
                                          :onChange    #(om/update-state! this assoc :chat-message (.-value (.-target %)))}))
              (my-dom/div (->> (css/grid-column)
                               (css/add-class :shrink))
                          (dom/a #js {:className "button green small"
                                      :onClick   #(do
                                                   (if (:query/auth (om/props this))
                                                     (do (om/transact! this `[(chat/send-message
                                                                                ~{:store (select-keys store [:db/id])
                                                                                  :text  chat-message})
                                                                              :query/chat])
                                                         (om/update-state! this assoc :chat-message ""))
                                                     #?(:cljs (js/alert "Log in to send chat messages"))))}
                                 (dom/span nil "Send"))))))
        ))))

(def ->StreamChat (om/factory StreamChat))