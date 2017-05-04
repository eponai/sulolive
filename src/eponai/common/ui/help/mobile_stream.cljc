(ns eponai.common.ui.help.mobile-stream
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    [eponai.common.routes :as routes]))

(defui MobileStream
  Object
  (render [this]
    (dom/div
      nil
      (dom/h1 nil "Mobile streaming")
      (dom/p nil
             (dom/span nil "This guide assumes you've already read the ")
             (dom/a {:href (routes/path :help/first-stream)} (dom/span nil "First Stream Guide"))
             (dom/span nil " as streaming from your phone or tablet is simliar to streaming from a computer."))
      (dom/p nil
             (dom/span nil
                       (str "To stream from a mobile device you'll need a video encoding app. On iPhone we've tried an app named "))
             (dom/a {:href "https://itunes.apple.com/us/app/live-air-solo-stream-live-video-on-the-go/id1051147032"}
                    "Live:Air Solo")
             (dom/span nil (str ". The rest of the guide is tailored for that app, but the concept will be the same for other apps as well."
                                " Basically, find the setting to add an RTMP destination or target, add SULO Live's Server URL and Stream Key"
                                " as described in the first stream guide. That's it!")))
      (dom/h2 nil "Streaming on iOS with Live:Air Solo")
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
                                        "Going Live on the First Stream Guide")))))))

(def ->MobileStream (om/factory MobileStream))

