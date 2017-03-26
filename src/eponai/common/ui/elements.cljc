(ns eponai.common.ui.elements
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.photo :as photo]
    #?(:cljs
       [eponai.web.utils :as utils])
    [om.dom :as dom]
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

(defn message-item [msg]
  (menu/item (css/add-class :message-container)
             (my-dom/div
               (->> (css/grid-row)
                    (css/align :top))
               (my-dom/div
                 (->> (css/grid-column)
                      (css/grid-column-size {:small 2 :large 2}))
                 (photo/circle {:src (get-in msg [:chat.message/user :user/photo :photo/path])}))
               (my-dom/div (css/grid-column)
                           (dom/small nil
                                      (dom/strong nil (str (get-in msg [:chat.message/user :user/email])
                                                           ": "))
                                      (dom/span nil (:chat.message/text msg)))))))

(defn message-list [messages & [opts]]
  (let [{:keys [editable?]} opts]
    (menu/vertical
      #?(:cljs
              (css/add-class :messages-list {:onMouseOver #(set! (.. (utils/element-by-id "the-sulo-app") -style -overflow-y) "hidden")
                                             :onMouseOut  #(set! (.. (utils/element-by-id "the-sulo-app") -style -overflow-y) "scroll")})
         :clj (css/add-class :messages-list))
      (map (fn [msg]
             (debug "MSG " msg)
             (message-item msg))
           messages))))
