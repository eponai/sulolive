(ns eponai.common.ui.store.stream-settings
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.stream :as stream]
    [eponai.client.parser.message :as msg]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug warn]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.chat :as chat]
    #?(:cljs
       [eponai.web.utils :as utils])
    [eponai.common.parser :as parser]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.store.common :as store-common]
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
     {:query/stream [:stream/state
                     :stream/token]}
     {:query/stream-config [:ui.singleton.stream-config/publisher-url]}
     {:proxy/chat (om/get-query chat/StreamChat)}
     {:query/auth [:db/id :user/email]}])

  Object
  (render [this]
    (let [{:keys [store]} (om/get-computed this)
          {:query/keys [stream stream-config]
           :as         props
           :proxy/keys [chat]} (om/props this)
          stream-state (:stream/state stream)
          stream-token (:stream/token stream)
          chat-message (:chat-message (om/get-state this))
          message (msg/last-message this 'stream-token/generate)]
      (dom/div
        {:id "sulo-stream-settings"}
        (dom/h1
          (css/show-for-sr)
          "Stream settings")
        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 12 :large 6})
            (dom/div
              (css/add-class :dashboard-section)
              (dom/div
                (css/add-class :section-title)
                (dom/h2 nil
                        (dom/span nil "Stream"))
                (dom/h3 (css/add-class :sl-tooltip)
                        (dom/span
                          (cond->> (css/add-classes [:label ])
                                   (= stream-state :stream.state/offline)
                                   (css/add-class :primary)
                                   (= stream-state :stream.state/online)
                                   (css/add-class :success)
                                   (= stream-state :stream.state/live)
                                   (css/add-class :highlight))
                          (name stream-state))
                        (when (= stream-state :stream.state/offline)
                          (dom/span (css/add-class :sl-tooltip-text)
                                    "See the help checklist below to get started streaming")))
                (dom/div
                  nil
                  (cond
                    (= stream-state :stream.state/online)
                    (dom/a
                      (->> (css/button {:onClick #(om/transact! this [(list 'stream/go-live {:store-id (:db/id store)}) :query/stream])})
                           (css/add-class :highlight))
                      (dom/strong nil "Go live!"))
                    (= stream-state :stream.state/live)
                    (dom/a
                      (css/button-hollow {:onClick #(om/transact! this [(list 'stream/end-live {:store-id (:db/id store)}) :query/stream])})
                      (dom/strong nil "End live")))
                  (dom/a
                    (css/button-hollow {:onClick #(binding [parser/*parser-allow-local-read* false]
                                                    (om/transact! this [:query/stream]))})
                    (dom/i {:classes ["fa fa-refresh fa-fw"]})
                    (when (or (nil? stream-state) (= stream-state :stream.state/offline))
                      (dom/strong nil "Refresh")))))
              (callout/callout-small
                nil
                (stream/->Stream
                  (om/computed
                    (:proxy/stream props)
                    {:hide-chat?            true
                     :store                 store
                     ;; TODO: Implement this in the new video player if we want to.
                     :video-player-opts     {:on-error-retry-forever? true
                                             :on-started-playing      #(when (= stream-state :stream.state/offline)
                                                                         (om/transact! this `[(~'stream/ensure-online
                                                                                                ~{:store-id (:db/id store)})
                                                                                              :query/stream]))}
                     ;; Allow all states, as there might be something wrong with the
                     ;; state change communication.
                     :allowed-stream-states #{:stream.state/offline
                                              :stream.state/live
                                              :stream.state/online}})))))
          (grid/column
            (grid/column-size {:small 12 :large 6})

            (dom/div
              (css/add-class :dashboard-section)
              (dom/div
                (css/add-class :section-title)
                (dom/h2 nil "Live chat"))
              (callout/callout-small
                (css/add-class :flex)
                (chat/->StreamChat (om/computed chat
                                                {:store store}))))))
        (grid/row-column
          nil
          (dom/div
            (css/add-class :dashboard-section)
            (callout/callout-small
              nil
              (grid/row
                (grid/columns-in-row {:small 3})
                (grid/column
                  (css/text-align :center)
                  (dom/h3 nil "Viewers")
                  (dom/p (css/add-class :stat) 0))
                (grid/column
                  (css/text-align :center)
                  (dom/h3 nil "Messages/min")
                  (dom/p (css/add-class :stat) 0))
                (grid/column
                  (css/text-align :center)
                  (dom/h3 nil "Payments")
                  (dom/p (css/add-class :stat) 0))))))

        (grid/row
          nil
          (grid/column
            (grid/column-size {:small 12 :large 6})
            (dom/div
              (css/add-class :dashboard-section)
              (dom/div
                (css/add-class :section-title)
                (dom/h2 nil "Encoder setup"))
              (callout/callout
                nil
                (grid/row
                  (css/align :bottom)
                  (grid/column
                    (grid/column-size {:small 12 :medium 8})
                    (dom/label nil "Server URL")
                    (dom/input {:type  "text"
                                :id    "input.stream-url"
                                :value (or (:ui.singleton.stream-config/publisher-url stream-config) "")})))
                (grid/row
                  (css/align :bottom)
                  (grid/column
                    (grid/column-size {:small 12 :medium 8})
                    (dom/label nil "Stream Key")
                    (dom/input {:type        "text"
                                :value       (or stream-token "")
                                ;:defaultValue (if (msg/final? message)
                                ;                (:token (msg/message message))
                                ;                "")
                                :placeholder "Create a stream key..."}))
                  (grid/column
                    nil
                    (dom/a
                      (css/button-hollow {:onClick #(msg/om-transact! this `[(stream-token/generate ~{:store-id (:db/id store)})
                                                                             {:query/stream [:stream/token]}])})
                      (dom/span nil "Create new key")))))))
          (grid/column
            nil
            (dom/div
              (css/add-class :dashboard-section)
              (dom/div
                (css/add-class :section-title)
                (dom/h2 nil "Setup checklist"))
              (callout/callout
                (css/add-class :setup-checklist)
                (dom/dl
                  nil
                  (dom/dt
                    nil
                    (dom/h3 nil (dom/small nil "Setup encoding software")))
                  (dom/dd
                    nil
                    (dom/p nil
                           (dom/span nil "Before you can start streaming on SULO Live, you need to download encoding software, and then set it up.
                         Learn more about setting up encoders in our ")
                           (dom/a {:href   (routes/url :help/first-stream)
                                   :target "_blank"}
                                  (dom/span nil "First Stream Guide"))
                           (dom/span nil ". You'll need to use the Server URL and Stream key to configure the encoding software.")))
                  (dom/dt
                    nil
                    (dom/h3 nil (dom/small nil "Publish stream")))
                  (dom/dd
                    nil
                    (dom/p nil (dom/span nil "To start streaming, start your encoder.
                  Hit Refresh and the status bar will indicate when your streaming content is being published to our servers.
                  At this point you'll be able to see a preview of your stream, but you're not yet visible to others on SULO Live.")))
                  (dom/dt
                    nil
                    (dom/h3 nil (dom/small nil "Go live")))
                  (dom/dd
                    nil
                    (dom/p nil (dom/span nil "To go live and hangout with your crowd, click 'Go live' and your stream will be visible on your store page.
                  To stop streaming, stop your encoder. Ready to go live again? Any time you send content, hit 'Go live' and you're live!"))))))))
        ))))

(def ->StreamSettings (om/factory StreamSettings))