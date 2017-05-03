(ns eponai.common.ui.help.mobile-stream
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.dom :as dom]
    ))

(defui MobileStream
  Object
  (render [this]
    (dom/div
      nil
      (dom/h1 nil "Mobile streaming")
      (dom/p nil
             (dom/span nil
                       (str "Publishing your stream on mobile is very similar to publishing a stream from a computer."
                            " You want to download a Video Encoding App. On iPhone we've tried an app named "))
             (dom/em nil "Live:Air Solo")
             (dom/span nil "."))
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
              (dom/li nil (dom/p nil (dom/span nil "Now you need to Go Live on SULO as well, see the Section about Going Live")))))))

(def ->MobileStream (om/factory MobileStream))

