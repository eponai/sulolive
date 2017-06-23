(ns eponai.common.ui.help
  (:require
    [eponai.common.ui.common :as common]
    [eponai.common.ui.help.first-stream :as first-stream]
    [eponai.common.ui.help.mobile-stream :as mobile-stream]
    [eponai.common.ui.help.quality :as stream-quality]
    [eponai.web.ui.help.shipping-rules :as shipping-rules]
    [eponai.common.ui.help.faq :as faq]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]
    [eponai.web.ui.footer :as foot]))

(def guides
  {:help/first-stream   {:guide       ::live-stream
                         :anchor-text "Setup your first stream"
                         :factory     first-stream/->FirstStream}
   :help/mobile-stream  {:guide       ::live-stream
                         :anchor-text "Stream via your mobile device"
                         :factory     mobile-stream/->MobileStream}
   :help/quality        {:guide       ::live-stream
                         :anchor-text "Stream quality recommendations"
                         :factory     stream-quality/->StreamQuality}
   :help/faq            {:guide       ::general
                         :anchor-text "Frequently Asked Questions"
                         :factory     faq/->FAQ}
   :help/fees           {:guide       ::general
                         :anchor-text "SULO Live service fee"
                         :factory     nil}
   :help/shipping-rules {:guide       ::general
                         :anchor-text "Shipping rules"
                         :factory     shipping-rules/->ShippingRules}})

(defn render-guide [route]
  (let [{:keys [guide anchor-text factory]} (get guides route)]
    (dom/div
      nil
      (menu/breadcrumbs
        nil
        (menu/item nil (dom/a {:href (routes/url :help)}
                              (dom/span nil (condp = guide
                                              ::live-stream "Live streaming guide"
                                              ::general "General"))))
        (menu/item nil (dom/span nil anchor-text)))
      (callout/callout
        nil
        (factory)))))

(defui Help
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/footer (om/get-query foot/Footer)}
     :query/current-route])
  Object
  (render [this]
    (let [{:proxy/keys [navbar footer]
           :query/keys [current-route]} (om/props this)
          {:keys [route]} current-route]
      (common/page-container
        {:navbar navbar :footer footer :id "sulo-help"}
        (grid/row-column
          nil
          (dom/div
            (css/add-class :app-title)
            (dom/a
              {:href (routes/url :help)}
              (dom/span nil "SULO Live help")))
          (if (contains? #{:help/first-stream :help/mobile-stream :help/faq :help/quality :help/shipping-rules} route)
            (render-guide route)
            (callout/callout
              nil
              ;(dom/article
              ;  nil
              ;  (dom/section
              ;    nil))
              (dom/h1 nil "SULO Live help")
              (dom/h2 nil "Guides")
              (menu/vertical
                nil
                (menu/item nil
                           (dom/strong nil "Live streaming")
                           (dom/ul
                             (css/add-class :nested)
                             (map (fn [route]
                                    (dom/li nil
                                            (dom/p nil
                                                   (dom/a {:href (routes/url route)}
                                                          (dom/span nil (get-in guides [route :anchor-text]))))))
                                  [:help/first-stream
                                   :help/mobile-stream
                                   :help/quality])))
                (menu/item nil
                           (dom/strong nil "General")
                           (dom/ul
                             (css/add-class :nested)
                             (map (fn [route]
                                    (let [{:keys [factory anchor-text]} (get guides route)]
                                      (dom/li
                                        nil
                                        (dom/p nil
                                               (dom/a {:href (when factory (routes/url route))}
                                                      ((if factory dom/span dom/s) nil anchor-text))))))
                                  [:help/shipping-rules
                                   :help/fees
                                   :help/faq]))))

              (dom/h2 nil "Contact us")
              (dom/p nil (dom/span nil "Do you still have questions? Contact us on ")
                     (dom/a {:href "mailto:help@sulo.live"} "help@sulo.live")
                     (dom/span nil ". We're happy to help!")))))))))

(def ->Help (om/factory Help))

(router/register-component :help Help)