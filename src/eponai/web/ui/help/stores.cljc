(ns eponai.web.ui.help.stores
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.help.first-stream :as first-stream]
    [eponai.common.ui.help.mobile-stream :as mobile-stream]
    [eponai.common.ui.help.quality :as stream-quality]
    [eponai.web.ui.help.shipping-rules :as shipping-rules]
    [eponai.web.ui.help.taxes :as taxes]
    [eponai.common.ui.help.faq :as faq]
    [taoensso.timbre :refer [debug]]))

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
                         :anchor-text "Shipping"
                         :factory     shipping-rules/->ShippingRules}
   :help/taxes          {:guide       ::general
                         :anchor-text "Taxes"
                         :factory     taxes/->Taxes}})

(defui Stores
  Object
  (render [this]
    (let [{:keys [current-route]} (om/get-computed this)
          {:keys [route]} current-route]
      (dom/div
        nil
        (callout/callout-small
          (css/add-class :help-menu)
          (grid/row-column
            nil
            (menu/breadcrumbs
              nil
              (menu/item nil (dom/a {:href (routes/url :help)}
                                    (dom/span nil "SULO Live support")))
              (menu/item nil (dom/span nil "Stores")))))
        (grid/row-column
          nil
          (dom/h1 nil (dom/span nil "Stores") (dom/br nil) (dom/small nil "Managing your SULO Live store"))

          (callout/callout
            nil
            (dom/h2 nil "Live streaming")

            (dom/div
              (css/add-class :help-items)
              (dom/section
                nil
                (dom/a
                  (->> {:href (routes/url :help/first-stream)}
                       (css/add-class :header))
                  (dom/h3 nil (dom/span nil (-> guides :help/first-stream :anchor-text))))
                )

              (dom/section
                nil
                (dom/a
                  (->> {:href (routes/url :help/mobile-stream)}
                       (css/add-class :header))
                  (dom/h3 nil (dom/span nil (-> guides :help/mobile-stream :anchor-text))))
                )
              (dom/section
                nil
                (dom/a
                  (->> {:href (routes/url :help/quality)}
                       (css/add-class :header))
                  (dom/h3 nil (dom/span nil (-> guides :help/quality :anchor-text))))
                ))

            (dom/h2 nil "General")
            (dom/div
              (css/add-class :help-items)
              (dom/section
                nil
                (dom/a
                  (->> {:href (routes/url :help/shipping-rules)}
                       (css/add-class :header))
                  (dom/h3 nil (dom/span nil (-> guides :help/shipping-rules :anchor-text))))
                )

              (dom/section
                nil
                (dom/a
                  (->> {:href (routes/url :help/taxes)}
                       (css/add-class :header))
                  (dom/h3 nil (dom/span nil (-> guides :help/taxes :anchor-text))))
                )
              (dom/section
                nil
                (dom/a
                  (->> {:href (routes/url :help/faq)}
                       (css/add-class :header))
                  (dom/h3 nil (dom/span nil (-> guides :help/faq :anchor-text))))
                ))))))))

(def ->Stores (om/factory Stores))