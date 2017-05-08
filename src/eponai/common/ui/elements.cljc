(ns eponai.common.ui.elements
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    #?(:cljs
       [eponai.web.utils :as utils])
    [taoensso.timbre :refer [debug]]
    [eponai.web.ui.photo :as p]))

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

(defn message-item [msg]
  (my-dom/li
    (css/add-class :message-container)
    (my-dom/div (css/add-class :message-photo)
                (p/user-photo (:chat.message/user msg) {:transformation :transformation/thumbnail-tiny}))
    (my-dom/div
      (css/add-class :message-text)
      (my-dom/strong nil (str (get-in msg [:chat.message/user :user/email])
                           ": "))
      (my-dom/span nil (:chat.message/text msg)))
    ;(my-dom/div
    ;  (->> (css/grid-row)
    ;       (css/align :top))
    ;  (my-dom/div
    ;    (->> (css/grid-column)
    ;         (css/grid-column-size {:small 2 :large 2}))
    ;    (photo/circle {:src (get-in msg [:chat.message/user :user/profile :user.profile/photo :photo/path])}))
    ;  ())
    ))

(defn message-list [messages & [opts]]
  (let [{:keys [editable?]} opts
        id "sl-chat-message-list"]
    (my-dom/div
      (css/add-class :message-list-container)
      (my-dom/ul
        #?(:cljs
                (css/add-class :messages-list {:onMouseOver #(set! (.. (utils/element-by-id "the-sulo-app") -style -overflow-y) "hidden")
                                               :onMouseOut  #(set! (.. (utils/element-by-id "the-sulo-app") -style -overflow-y) "scroll")
                                               :id          id})
           :clj (css/add-class :messages-list {:id id}))
        (map message-item messages)))))
