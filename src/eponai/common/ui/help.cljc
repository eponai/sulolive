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
    [eponai.web.ui.photo :as photo]))

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

(defn render-guide [route]
  (let [{:keys [guide anchor-text factory]} (get guides route)]
    (dom/div
      nil
      (menu/breadcrumbs
        nil
        (menu/item nil (dom/a {:href (routes/url :help)}
                              "SULO Live Help"))
        (menu/item nil (dom/a {:href (routes/url :help)}
                              (dom/span nil (condp = guide
                                              ::live-stream "Live streaming guide"
                                              ::general "General"))))
        (menu/item nil (dom/span nil anchor-text)))
      (dom/h1 nil (str anchor-text))
      (callout/callout
        nil
        (factory))

      (callout/callout
        nil
        (dom/h2 nil "Still have questions?")
        (dom/p nil
               (dom/span nil "Contact us ")
               (dom/a {:href "mailto:hello@sulo.live"} "hello@sulo.live")
               (dom/span nil ". Miriam, Diana or Petter will help you out."))))))

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
                       (photo/square {:src "//p6.zdassets.com/hc/theme_assets/224203/200019615/201998438.svg"})
                       (dom/strong nil "Welcome"))))
                 (grid/column
                   (css/add-class :sulo-help-section)
                   (dom/a
                     {:href (routes/url :help/accounts)}
                     (callout/callout-small
                       nil
                       (photo/square {:src "//p6.zdassets.com/hc/theme_assets/224203/200019615/201998438.svg"})
                       (dom/strong nil "Accounts"))))
                 (grid/column
                   (css/add-class :sulo-help-section)
                   (dom/a
                     {:href (routes/url :help/stores)}
                     (callout/callout-small
                       nil
                       (photo/square {:src "//p6.zdassets.com/hc/theme_assets/224203/200019615/201998438.svg"})
                       (dom/strong nil "Stores")))))]
              (= route :help/welcome)
              [(callout/callout-small
                 (css/add-class :help-menu)
                 (grid/row-column
                   nil
                   (menu/breadcrumbs
                     nil
                     (menu/item nil (dom/a {:href (routes/url :help)}
                                           "SULO Live support"))
                     (menu/item nil (dom/a {:href (routes/url :help/welcome)}
                                           (dom/span nil "Welcome"))))))
               (grid/row-column
                 nil
                 (dom/h1 nil "Welcome"))]

              (= route :help/stores)
              [(callout/callout-small
                 (css/add-class :help-menu)
                 (grid/row-column
                   nil
                   (menu/breadcrumbs
                     nil
                     (menu/item nil (dom/a {:href (routes/url :help)}
                                           "SULO Live support"))
                     (menu/item nil (dom/a {:href (routes/url :help/stores)}
                                           (dom/span nil "Stores"))))))
               (grid/row-column
                 nil
                 (dom/h1 nil "Stores") )]

              (= route :help/accounts)
              (accounts/->Accounts))


        ;(if (contains? #{:help/first-stream :help/mobile-stream :help/faq :help/quality :help/shipping-rules :help/taxes} route)
        ;  (render-guide route)
        ;  (dom/div
        ;    nil
        ;    (dom/div
        ;      (css/add-class :app-title)
        ;      (dom/h1 nil "SULO Live help"))
        ;    (callout/callout
        ;      nil
        ;      ;(dom/article
        ;      ;  nil
        ;      ;  (dom/section
        ;      ;    nil))
        ;
        ;      (dom/section
        ;        nil
        ;        (dom/h2 nil "Accounts")
        ;        (dom/h3 nil "Sign up / Sign in")
        ;        (menu/vertical
        ;          nil
        ;          (dom/ul
        ;            (css/add-class :nested)
        ;            (map (fn [route]
        ;                   (dom/li nil
        ;                           (dom/p nil
        ;                                  (dom/a {:href (routes/url route)}
        ;                                         (dom/span nil (get-in guides [route :anchor-text]))))))
        ;                 [:help/first-stream
        ;                  :help/mobile-stream
        ;                  :help/quality]))))
        ;      (dom/section
        ;        nil
        ;        (dom/h2 nil "Stores")
        ;        (dom/h3 nil "General")
        ;        (dom/ul
        ;          (css/add-class :nested)
        ;          (map (fn [route]
        ;                 (let [{:keys [factory anchor-text]} (get guides route)]
        ;                   (dom/li
        ;                     nil
        ;                     (dom/p nil
        ;                            (dom/a {:href (when factory (routes/url route))}
        ;                                   ((if factory dom/span dom/s) nil anchor-text))))))
        ;               [:help/shipping-rules
        ;                :help/taxes
        ;                :help/faq]))
        ;        (dom/h3 nil "Live streaming")
        ;        (menu/vertical
        ;          nil
        ;          (dom/ul
        ;            (css/add-class :nested)
        ;            (map (fn [route]
        ;                   (dom/li nil
        ;                           (dom/p nil
        ;                                  (dom/a {:href (routes/url route)}
        ;                                         (dom/span nil (get-in guides [route :anchor-text]))))))
        ;                 [:help/first-stream
        ;                  :help/mobile-stream
        ;                  :help/quality]))))
        ;
        ;      (dom/h2 nil "Contact us")
        ;      (dom/p nil (dom/span nil "Do you still have questions? Contact us on ")
        ;             (dom/a {:href "mailto:help@sulo.live"} "help@sulo.live")
        ;             (dom/span nil ". We're happy to help!")))))
        ))))

(def ->Help (om/factory Help))

(router/register-component :help Help)