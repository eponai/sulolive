(ns eponai.common.ui.help
  (:require
    [eponai.common.ui.help.first-stream :as first-stream]
    [eponai.common.ui.help.mobile-stream :as mobile-stream]
    [eponai.common.ui.help.quality :as stream-quality]
    [eponai.web.ui.help.shipping-rules :as shipping-rules]
    [eponai.common.ui.help.faq :as faq]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.help.taxes :as taxes]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.client.routes :as routes]
    [eponai.web.ui.help.accounts :as accounts]
    [eponai.web.ui.help.welcome :as welcome]
    [eponai.web.ui.help.stores :as stores]
    [eponai.web.ui.photo :as photo]))

(defn render-contact-us []
  (grid/row-column
    nil
    (callout/callout
      (css/add-class :still-questions)
      (dom/h2 nil "Still have questions?")
      (dom/p nil
             (dom/span nil "Don't hesitate to contact us ")
             (dom/a {:href "mailto:help@sulo.live"} "help@sulo.live")
             (dom/span nil ". Miriam, Diana or Petter will help you out.")))))

(defn render-guide [route]
  (let [{:keys [guide anchor-text factory]} (get stores/guides route)]
    (dom/div
      nil
      (callout/callout
        (css/add-class :help-menu)
        (grid/row-column
          nil
          (menu/breadcrumbs
            nil
            (menu/item nil (dom/a {:href (routes/url :help)}
                                  (dom/span nil "SULO Live support")))
            (menu/item nil (dom/a {:href (routes/url :help/stores)}
                                  (dom/span nil "Shops")))
            (menu/item nil (dom/span nil anchor-text)))))
      (grid/row-column
        nil
        (factory)

        (render-contact-us)))))

(defui Help
  static om/IQuery
  (query [_]
    [{:query/auth [:user/email]}
     :query/current-route])
  Object
  (render [this]
    (let [{:query/keys [current-route]} (om/props this)
          {:keys [route]} current-route]
      (dom/div
        nil
        (if (contains? #{:help/first-stream :help/mobile-stream :help/faq :help/quality :help/shipping-rules :help/taxes} route)
          (render-guide route)
          (cond (= route :help)
                [
                 (grid/row
                   (css/add-classes [:collapse :expanded])
                   (photo/cover {:photo-id "static/help"}))
                 (grid/row-column
                   nil
                   (dom/div
                     (css/add-class :app-title)
                     (dom/h1 nil "SULO Live help center")))
                 (grid/row
                   (->> (grid/columns-in-row {:small 2 :medium 3})
                        (css/add-class :sulo-help-sections))
                   (grid/column
                     (css/add-class :sulo-help-section)
                     (dom/a
                       {:href (routes/url :help/welcome)}
                       (callout/callout-small
                         nil
                         (photo/square {:photo-id "static/welcome"})
                         (dom/h3 nil "Welcome"))))
                   (grid/column
                     (css/add-class :sulo-help-section)
                     (dom/a
                       {:href (routes/url :help/accounts)}
                       (callout/callout-small
                         nil
                         (photo/square {:photo-id "static/help-profile"})
                         (dom/h3 nil "Accounts"))))
                   (grid/column
                     (css/add-class :sulo-help-section)
                     (dom/a
                       {:href (routes/url :help/stores)}
                       (callout/callout-small
                         nil
                         (photo/square {:photo-id "static/help-store"})
                         (dom/h3 nil "Shops")))))]
                (= route :help/welcome)
                [(welcome/->Welcome)
                 (render-contact-us)]

                (= route :help/stores)
                [(stores/->Stores (om/computed {} {:current-route current-route}))
                 (render-contact-us)]

                (= route :help/accounts)
                [(accounts/->Accounts)
                 (render-contact-us)]))
        ))))

(def ->Help (om/factory Help))

(router/register-component :help Help)