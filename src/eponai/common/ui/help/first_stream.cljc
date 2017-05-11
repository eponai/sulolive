(ns eponai.common.ui.help.first-stream
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.routes :as routes]
    [eponai.web.ui.photo :as p]
    [eponai.common.ui.elements.callout :as callout]))

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
            (p/square {:src "/assets/img/obs-logo.png"}))
          (grid/column
            nil
            (dom/p nil
                   (dom/a {:href   "https://obsproject.com/"
                           :target "_blank"}
                          (dom/span nil "Download Open Broadcaster Software")))))

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
                         (dom/strong nil "Preferences > Stream")
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

        (dom/h2 nil "Organize your window")
        (dom/p nil
               (dom/span nil "Before publishing your stream, you need to setup the content that should be published in the stream. You arrange what content your audience will see using ")
               (dom/em nil "Scenes")
               (dom/span nil "."))
        (dom/h3 nil "Add a scene")
        (dom/p nil
               (dom/span nil "Scenes contain the content you want your audience to see."))
        (dom/ol
          nil
          (dom/li nil
                  (dom/p nil (dom/span nil "To add a new scene, click the ")
                         (dom/strong nil "+")
                         (dom/span nil " in the bottom left corner.")))
          (dom/li nil
                  (dom/p nil (dom/span nil "Name your scene describing its content and click ")
                         (dom/strong nil "OK")
                         (dom/span nil "."))))
        (dom/p nil (dom/span nil "You'll see the content of your scene in the preview window, but it's probably looking a little empty. With no ")
               (dom/em nil " Sources ")
               (dom/span nil " setup yet, your content is only a black screen."))

        (dom/p nil
               (dom/strong nil "Tip: ")
               (dom/span nil "You can have multiple scenes setup and toggle between them as you are live streaming."))

        (dom/h3 nil "Add a source")
        (dom/p nil (dom/span nil "Sources are the places your content is captured from, this can be a window on your computer, your webcam, or an external device like a video camera."))
        (dom/ol nil
                (dom/li nil
                        (dom/p nil (dom/span nil "To add a new source to your scene, click the ")
                               (dom/strong nil "+")
                               (dom/span nil " in the bottom left corner.")))
                (dom/li nil
                        (dom/p nil (dom/span nil "You'll see a list of available sources to setup, for this example select ")
                               (dom/strong nil "Video Capture Device")
                               (dom/span nil ". We will setup our webcam to capture video for the stream.")))
                (dom/li nil
                        (dom/p nil
                               (dom/span nil "Check ") (dom/strong nil "Create new")
                               (dom/span nil " and name your source to something descriptive. Check ")
                               (dom/strong nil "Make source visible")
                               (dom/span nil " and click ")
                               (dom/strong nil "OK")))
                (dom/li nil
                        (dom/p nil
                               (dom/span nil "In the Window menu, select your web camera and check ")
                               (dom/strong nil "Use preset")
                               (dom/span nil ". You should see the captured video in the preview window. Click ")
                               (dom/strong nil "OK")
                               (dom/span nil " when you're ready."))))
        (dom/p nil (dom/span nil "We now have video being captured from our web camera. You should be able to see your video in the scene preview."))
        ;(callout/callout-small
        ;  (css/add-class :action))
        ;(dom/h4 nil (dom/div {:classes ["icon icon-idea"]})
        ;        (dom/span nil "Tip"))
        (dom/p nil
               (dom/strong nil "Tip: ")
               (dom/span nil "Try moving and resizing the source in the preview window. You can add more sources to the same scene and arrange them with the move and resize tools."))



        (dom/h2 nil "Publish stream")
        (dom/p nil
               (dom/span nil " When you like how your stream looks, just click ")
               (dom/strong nil "Start Streaming")
               (dom/span nil " in the bottom right corner of Open Broadcaster Software to publish your stream. We are now receiving your stream on SULO Live!"))
        (dom/h2 nil "Going live")
        (dom/p nil
               (dom/span nil "Once you've published your stream, only you will be able to see it in your stream preview on SULO Live. Click ")
               (dom/strong nil "Go live")
               (dom/span nil " to make your stream public and show up on your store page."))
        (dom/p nil (dom/strong nil "I can't see my stream preview"))
        (dom/ul nil
                (dom/li nil
                        (dom/p nil (dom/span nil "There's a time delay in the video delivery from our servers. Try clicking ")
                               (dom/em nil "Refresh")
                               (dom/span nil " to reload the video preview.")))
                (dom/li nil
                        (dom/p nil (dom/span nil "Try stopping the stream in OBS, wait a few moments and start again. Click ")
                               (dom/em nil  "Refresh")
                               (dom/span nil " in your stream settings."))))

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
