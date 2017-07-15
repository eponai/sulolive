(ns eponai.common.ui.help.quality
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.callout :as callout]))

(defui StreamQuality
  Object
  (render [this]
    (dom/div
      nil
      (dom/h1 nil "Stream Quality settings")

      (callout/callout
        nil
        (dom/p nil (str "Adjusting quality settings can help reduce buffering issues, reduce lag and increase the"
                        " quality of your stream. All of these settings will have to be adjusted depending on"
                        " what type of machine you're streaming from. Since there's a lot to cover we will try"
                        " to keep our recommendation quite simple, and if you're interested in learning more"
                        " you can try youtube tutorials on OBS Quality Settings."))
        (dom/h2 nil "Open Broadcaster Software Quality Settings")
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
                                               (dom/span nil (str ": 30.")))))))))))

(def ->StreamQuality (om/factory StreamQuality))
