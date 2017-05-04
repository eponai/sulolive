(ns eponai.common.ui.help.first-stream
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.routes :as routes]))

(defui FirstStream
  Object
  (render [this]
    (dom/div
      nil
      (dom/h1 nil "Your first stream")
      (dom/div
        nil
        (dom/p nil
               (dom/span nil
                         (str "In this guide we'll get you streaming from your first time from your computer to SULO Live. If you want"
                              " to set up streaming on your mobile device instead of your computer, we recommend you read this"
                              " guide first, then head over to the "))
               (dom/a {:href (routes/path :help/mobile-stream)} "Mobile Stream Guide"))
        (dom/p nil "Before you can start streaming on SULO Live, you need to download encoding software. Encoding software allows you to capture content, including your desktop, camera, microphone, and more, and send it to SULO Live to be streamed to all your fans.")

        (dom/h2 nil "Download encoding software")
        (dom/p nil "The best encoding software for you depends on your needs. If you're not already familiar with encoding software we recommend you try Open Broadcaster Software. It is a free and open source software for video recording and live streaming, and it's easy to setup with SULO Live.")
        (grid/row
          (css/align :middle)
          (grid/column
            (grid/column-size {:small 3 :medium 2 :large 1})
            (photo/square {:src "/assets/img/obs-logo.png"}))
          (grid/column
            nil
            (dom/a {:href   "https://obsproject.com/"
                    :target "_blank"}
                   (dom/span nil "Download Open Broadcaster Software"))))

        (dom/h2 nil "Configure Open Broadcaster Software")
        (dom/ol
          nil
          (dom/li nil
                  (dom/p nil
                         (dom/span nil "To publish your stream, you'll need a ")
                         (dom/em nil "Server URL")
                         (dom/span nil " and ")
                         (dom/em nil "Stream key")
                         (dom/span nil " for your store on SULO Live. You can find them in the stream settings.")))
          (dom/li nil
                  (dom/p nil "Install Open Broadcaster Software on your computer and start the software."))
          (dom/li nil
                  (dom/p nil
                         (dom/span nil "Go to ")
                         (dom/em nil "Preferences > Stream")
                         (dom/span nil " and cofigure with the following setup:"))
                  (dom/div
                    nil
                    (grid/row
                      nil
                      (grid/column
                        (grid/column-size {:small 12 :medium 3})
                        (dom/label nil "Stream Type:"))
                      (grid/column
                        (grid/column-size {:small 12 :medium 5})
                        (dom/input {:value    "Custom Streaming Server"
                                    :type     "text"
                                    :disabled true})))
                    (grid/row
                      nil
                      (grid/column
                        (grid/column-size {:small 12 :medium 3})
                        (dom/label nil "URL:"))
                      (grid/column
                        (grid/column-size {:small 12 :medium 5})
                        (dom/input {:value    "<your Server URL>"
                                    :type     "text"
                                    :disabled true})))
                    (grid/row
                      nil
                      (grid/column
                        (grid/column-size {:small 12 :medium 3})
                        (dom/label nil "Stream key:"))
                      (grid/column
                        (grid/column-size {:small 12 :medium 5})
                        (dom/input {:value    "<your Stream key>"
                                    :type     "text"
                                    :disabled true}))))
                  ;(dom/ul nil
                  ;        (dom/li nil
                  ;                (dom/span nil "Stream Type: Custom Streaming Server"))
                  ;        (dom/li nil
                  ;                (dom/span nil "URL: <your Server URL>"))
                  ;        (dom/li nil
                  ;                (dom/span nil "Stream key: <your Stream key>")))
                  ))

        (dom/h2 nil "Publish stream")
        (dom/p nil
               (dom/span nil "When you've organized your window to how you like it, just click the ")
               (dom/em nil "Start Streaming")
               (dom/span nil " button in the bottom right corner of Open Broadcaster Software to publish your stream!"))
        (dom/h2 nil "Going live")
        (dom/p nil
               (dom/span nil (str "Before your customers can see you on SULO Live you'll have to \"Go live\"."
                                  " You can go live once you've published your stream by navigating to your "))
               (dom/em nil "Store Dashboard")
               (dom/span nil " click the ")
               (dom/em nil "Stream")
               (dom/span nil " tab and press ")
               (dom/em nil "Go Live")
               (dom/span nil (str " button to the right on the page.")))
        (dom/p nil
               (dom/span nil (str "If you can't see the Go Live button, it's probably because the stream"
                                  " hasn't been succesfully published yet. Try pressing the ")
                         )
               (dom/em nil "Refresh")
               (dom/span nil (str " button, to see if the Go Live button appears. If it still doesn't work try"
                                  " stopping then starting the stream again, hit refresh and the Go Live button"
                                  " should appear. When you see it, click it!")))
        (dom/h2 nil "What now?")
        (dom/p nil
               (dom/span nil (str "Congratulations! You've gone live for the first of many times!"
                                  " Streaming live to customers will take some time getting used to"
                                  " and we want to help you as much as we can.")))
        (dom/p nil
               (dom/span nil
                         (str " If you want to stream while you're on the go, or need more flexible"
                              " cameras than your webcam or laptop cam, see the "))
               (dom/a {:href (routes/path :help/mobile-stream)}
                      (dom/span nil "Mobile Streaming Guide")))
        (dom/p nil
               (dom/span nil " If you're having problems with quality or latency, check out the ")
               (dom/a {:href (routes/path :help/quality)}
                      (dom/span nil "Quality Settings Guide")))
        (dom/p nil
               (dom/span nil
                         (str " If you want to get inspiration and see what other people are doing"
                              " with live streaming we recommend you checkout other creatives at: "))
               (dom/a {:href "https://www.twitch.tv/directory/game/Creative"}
                      (dom/span nil "twitch.tv/creative")))
        (dom/h2 nil "More questions?")
        (dom/p nil (dom/span nil "Do you still have questions? Contact us on ")
               (dom/a {:href "mailto:help@sulo.live"}
                      (dom/span nil "help@sulo.live"))
               (dom/span nil ". We're happy to help!"))))))

(def ->FirstStream (om/factory FirstStream))
