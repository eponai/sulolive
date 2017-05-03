(ns eponai.common.ui.encoding-guide
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.navbar :as nav]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.css :as css]))

(defui EncodingGuide
  Object
  (render [_]
    (dom/div
      nil
      (menu/breadcrumbs
        nil
        (menu/item nil (dom/a nil (dom/span nil "Live streaming guide")))
        (menu/item nil (dom/span nil "Setup your encoder")))
      (grid/row-column
        nil
        (callout/callout
          (css/add-class :large)
          ;(dom/article
          ;  nil
          ;  (dom/section
          ;    nil))
          (dom/h1 nil "Setup your encoder")

          (dom/div
            nil
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
            (dom/p nil (dom/span nil (str " If you're having problems with quality or latency, check out the"
                                          " Quality settings section further down on this page.")))
            (dom/p nil
                   (dom/span nil
                             (str " If you want to stream while you're on the go, or need more flexible"
                                  " cameras than your webcam or laptop cam, see the Mobile streaming section.")))
            (dom/p nil
                   (dom/span nil
                             (str " If you want to get inspiration and see what other people are doing"
                                  " with live streaming we recommend you checkout other creatives at: "))
                   (dom/a {:href "https://www.twitch.tv/directory/game/Creative"}
                          (dom/span nil "twitch.tv/creative")))
            (dom/h2 nil "Mobile streaming")
            (dom/p nil
                   (dom/span nil
                             (str "Publishing your stream on mobile is very similar to publishing a stream from a computer."
                                  " You want to download a Video Encoding App. On iPhone we've tried an app named "))
                   (dom/em nil "Live:Air Solo")
                   (dom/span nil "."))
            (dom/h3 nil "Streaming on iOS with Live:Air Solo")
            (dom/ol nil
                    (dom/li nil (dom/p nil (dom/span nil "Press the Gears (Settings) button in the top left corner.")))
                    (dom/li nil
                            (dom/p nil
                                   (dom/span nil "Press ")
                                   (dom/em nil "Output")
                                   (dom/span nil " > ")
                                   (dom/em nil "Broadcasting Destinations")
                                   (dom/span nil " > ")
                                   (dom/em nil "Manage Destinations")
                                   (dom/span nil " > ")
                                   (dom/em nil "Add Destination")))
                    (dom/li nil
                            (dom/p nil
                                   (dom/span nil "Scroll to and press ")
                                   (dom/em nil "CUSTOM RTMP")
                                   (dom/span nil " and fill out the form:"))
                            (dom/ul nil
                                    (dom/li nil (dom/p nil
                                                       (dom/span nil "In the URL field, enter the ")
                                                       (dom/em nil "Server URL")
                                                       (dom/span nil " found in the Stream tab on your Store Dashboard")))
                                    (dom/li nil (dom/p nil
                                                       (dom/span nil "In the Stream field, enter the ")
                                                       (dom/em nil "Stream Key")
                                                       (dom/span nil " found in the Stream tab on your Store Dashboard")))
                                    (dom/li nil (dom/p nil (dom/span nil "Select FMLE as User Agent")))
                                    (dom/li nil (dom/p nil (dom/span nil "Leave User and Password empty")))))
                    (dom/li nil (dom/p nil
                                       (dom/span nil "Press ")
                                       (dom/em nil "Done")
                                       (dom/span nil " in the top right corner to save")))
                    (dom/li nil (dom/p nil
                                       (dom/span nil "Go all the way back to the first screen where you see the camera and press ")
                                       (dom/em nil "Go Live !")
                                       (dom/span nil " in the top right corner")))
                    (dom/li nil (dom/p nil (dom/span nil "Now you need to Go Live on SULO as well, see the Section about Going Live"))))
            (dom/h2 nil "Quality settings")
            (dom/p nil (str "Adjusting quality settings can help reduce buffering issues, reduce lag and increase the"
                            " quality of your stream. All of these settings will have to be adjusted depending on"
                            " what type of machine you're streaming from. Since there's a lot to cover we will try"
                            " to keep our recommendation quite simple, and if you're interested in learning more"
                            " you can try youtube tutorials on OBS Quality Settings."))
            (dom/h3 nil "Open Broadcaster Software Settings")
            (dom/ul nil
                    (dom/li nil (dom/p nil
                                       (dom/span nil "First open Open Broadcaster Software and click the ")
                                       (dom/em nil "Settings")
                                       (dom/span nil " button.")))
                    (dom/li nil
                            (dom/p nil
                                   (dom/span nil "Select the ")
                                   (dom/em nil "Output")
                                   (dom/span nil " section."))
                            (dom/ol nil
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Output Mode")
                                                   (dom/span nil ": Advanced")))
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Encoder")
                                                   (dom/span nil (str ": if you have an option other than x264"
                                                                      " that does not say \"Software\", choose that one."
                                                                      " Otherwise select x264 and use the following settings."
                                                                      " If you're choosing different encoder other than x264,"
                                                                      "  apply all the following settings that you can see for"
                                                                      " your encoder."))))
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Rescale output")
                                                   (dom/span nil (str ": select a value between 1280x720 to 720x576"
                                                                      " (your values might be slightly different)."
                                                                      " The larger the number, the higher the quality,"
                                                                      " but higher quality always comes at the cost of"
                                                                      " computing power. You shouldn't have to go lower"
                                                                      " than 720x576"))))
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Rate Control")
                                                   (dom/span nil (str ": CBR. CBR will keep the bandwidth of"
                                                                      " your stream the same, which means there will"
                                                                      " be less quality spikes that may cause lag spikes"
                                                                      " for your viewers."))))
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Keyframe Interval")
                                                   (dom/span nil (str ": 0 or 1. Enables us at SULO Live"
                                                                      " to reduce latency between streamer and"
                                                                      " viewer."))))
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Bitrate")
                                                   (dom/span nil (str ": select a value between 1000 and 2000."
                                                                      " This value what will impact the quality"
                                                                      " and buffering the most. If you set it"
                                                                      " too high, either your computer or network"
                                                                      " connection won't be able to handle it, causing"
                                                                      " your stream to appear to lag. Set too low"
                                                                      " and you won't be delivering as much quality"
                                                                      " as you could have to your viewers."))))
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "CPU Usage Preset")
                                                   (dom/span nil (str ": veryfast or ultrafast. Selecting"
                                                                      " ultrafast can reduce the quality"
                                                                      " of your stream, but it can also help"
                                                                      " if you're experiencing frame drops, i.e."
                                                                      " a laggy stream."))))
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Profile")
                                                   (dom/span nil (str ": (None)"))))
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Tune")
                                                   (dom/span nil (str ": fastdecode. Might decrease"
                                                                      " the quality of your stream, but"
                                                                      " reduces the amount of latency between"
                                                                      " you and your viewers. Setting it to zerolatency"
                                                                      " will decrease the latency by an additional"
                                                                      " few hundred ms."))))))
                    (dom/li nil
                            (dom/p nil
                                   (dom/span nil (str "In the left menu where you selected Output earlier"
                                                      " select: "))
                                   (dom/em nil "Video"))
                            (dom/ol nil
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Base (Canvas) Resolution")
                                                   (dom/span nil (str ": leave it. You should be able to keep this"
                                                                      " value quite high without getting lag. This"
                                                                      " is how large your streaming canvas will be."))))
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Output (Scaled) Resolution")
                                                   (dom/span nil (str ": if you're using the x264 encoder, set this"
                                                                      " value to the same as the \"Rescale output\""
                                                                      " value we set earlier. If you're not using"
                                                                      " the x264 encoder, set this to a value between"
                                                                      " 1280x720 to 720x576. See the \"Rescale output\""
                                                                      " section above."))))
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Downscale Filter")
                                                   (dom/span nil (str ": Bilinear (Fastest, but blurry if scaling)."
                                                                      " if you've got a powerful machine, you can try"
                                                                      " the other options."))))
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/em nil "Common FPS Values")
                                                   (dom/span nil (str ": 30.")))))))
            (dom/h2 nil "More questions?")
            (dom/p nil (dom/span nil "Do you still have questions? Contact us on ")
                   (dom/a {:href "mailto:help@sulo.live"}
                          (dom/span nil "help@sulo.live"))
                   (dom/span nil ". We're happy to help!")))))
      )))

(def ->EncodingGuide (om/factory EncodingGuide))