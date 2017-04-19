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
                            (dom/input {:value "Custom Streaming Server"
                                        :type  "text"
                                        :disabled true})))
                        (grid/row
                          nil
                          (grid/column
                            (grid/column-size {:small 12 :medium 3})
                            (dom/label nil "URL:"))
                          (grid/column
                            (grid/column-size {:small 12 :medium 5})
                            (dom/input {:value "<your Server URL>"
                                        :type  "text"
                                        :disabled true})))
                        (grid/row
                          nil
                          (grid/column
                            (grid/column-size {:small 12 :medium 3})
                            (dom/label nil "Stream key:"))
                          (grid/column
                            (grid/column-size {:small 12 :medium 5})
                            (dom/input {:value "<your Stream key>"
                                        :type  "text"
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
                   (dom/span nil "When you've organized your window to how you like it, just click ")
                   (dom/em nil "Start Streaming")
                   (dom/span nil " to publish your stream to SULO Live! You'll see your stream come online in your stream settings where you can go live.")))
          (dom/h2 nil "More questions?")
          (dom/p nil (dom/span nil "Do you still have questions? Contact us on ")
                 (dom/a {:href "mailto:help@sulo.live"} "help@sulo.live")
                 (dom/span nil ". We're happy to help!"))))
      )))

(def ->EncodingGuide (om/factory EncodingGuide))