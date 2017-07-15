(ns eponai.common.ui.help.mobile-stream
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.routes :as routes]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.callout :as callout]))

(defui MobileStream
  Object
  (render [this]
    (dom/div
      nil
      (dom/h1 nil "Mobile streaming")

      (callout/callout
        nil
        (dom/p nil
               (dom/span nil "This guide will walk you through how to setup a stream to SULO Live from your mobile device. If you want to stream via your computer, take a look at our ")
               (dom/a {:href (routes/path :help/first-stream)} "First Stream Guide")
               (dom/span nil "."))
        (dom/h2 nil "Getting started")
        (dom/h3 nil "Download a video encoding app")
        (dom/p nil "Before you can start streaming on SULO Live from your mobile device, you need to download a video encoding app. Encoding software allows you to capture content, like your camera, microphone, and more, and send it to SULO Live to be streamed to all your fans. On iPhone/iPad we have tried two different apps;")

        (dom/ul nil
                (dom/li nil
                        (dom/p nil (dom/a {:href "https://itunes.apple.com/us/app/live-now-start-your-live-video-broadcast/id1097020890?mt=8"}
                                          "Live Now")
                               (dom/span nil " - a basic, free app that is easy to operate. If you are just starting out with streaming and are happy with a single camera view, this app is a really good starting point.")))
                (dom/li nil
                        (dom/p nil (dom/a {:href "https://itunes.apple.com/us/app/live-air-solo-stream-live-video-on-the-go/id1051147032?mt=8"} "Live:Air Solo")
                               (dom/span nil " - a little more advanced app which charges you $49 for the cheapest version. They have a free version, but that comes with time limit on your streams and a watermark in the corner. This app allows you to organize your stream with different views and transitions."))))
        (callout/callout-small
          (css/add-class :sulo)
          (dom/p nil (dom/small nil (str "This guide is tailored for the two apps mentioned above, but any app that can stream to an RTMP server would work."
                                         " Find the setting to add an RTMP destination or target, add SULO Live's Server URL and Stream Key"
                                         " as described in the first stream guide. That's it!"))))

        (dom/h3 nil "Get your SULO Live settings")
        (dom/p nil "You will need two things to configure your mobile app to publish your stream to SULO Live:")
        (dom/ol nil (dom/li nil (dom/p nil (dom/span nil "Your ") (dom/strong nil "Server URL")))
                (dom/li nil (dom/p nil (dom/span nil "Your ") (dom/strong nil "Stream key"))))
        (dom/p nil
               (dom/span nil "You can find the information under ")
               (dom/em nil "Live stream")
               (dom/span nil " in your store settings, which is also where you will be able to preview your stream and eventually go live."))

        (dom/h2 nil (dom/span nil "Streaming on iOS with ")
                (dom/a {:href "https://itunes.apple.com/us/app/live-now-start-your-live-video-broadcast/id1097020890?mt=8"}
                       "Live Now"))
        (dom/ol
          nil
          (dom/li nil (dom/p nil (dom/span nil "Open the app")))
          (dom/li nil
                  (dom/p nil (dom/span nil "Configure the app for streaming to SULO Live:"))
                  (dom/ul nil (dom/li nil (dom/p nil (dom/span nil "In the ")
                                                 (dom/em nil "Stream server")
                                                 (dom/span nil " field, enter your Server URL")))
                          (dom/li nil (dom/p nil (dom/span nil "In the ")
                                             (dom/em nil "Stream name/key")
                                             (dom/span nil " field, enter your Stream key"))))
                  )

          (dom/li nil (dom/p nil (dom/span nil "Press the ")
                             (dom/em nil "GO LIVE")
                             (dom/span nil " at the bottom of the screen, and voila! You are publishing your stream to SULO")))
          (dom/li nil (dom/p nil (dom/span nil "You'll be able to see your stream in your streaming settings. To let other users see your stream you need to go live on SULO too. See the Section about ")
                             (dom/a {:href (str (routes/path :help/first-stream) "#going-live")}
                                    "Going Live on the First Stream Guide"))))
        (dom/h2 nil (dom/span nil "Streaming on iOS with ")
                (dom/a {:href "https://itunes.apple.com/us/app/live-air-solo-stream-live-video-on-the-go/id1051147032?mt=8"} "Live:Air Solo"))
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
                (dom/li nil (dom/p nil (dom/span nil "Now you need to Go Live on SULO as well, see the Section about ")
                                   (dom/a {:href (str (routes/path :help/first-stream) "#going-live")}
                                          "Going Live on the First Stream Guide"))))))))

(def ->MobileStream (om/factory MobileStream))

