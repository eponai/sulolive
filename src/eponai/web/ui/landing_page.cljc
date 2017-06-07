(ns eponai.web.ui.landing-page
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [taoensso.timbre :refer [debug]]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.web.ui.button :as button]
    [eponai.client.auth :as auth]
    [eponai.common.shared :as shared]))

(defui LandingPage
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/featured-items [:db/id
                             :store.item/name
                             :store.item/price
                             {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                                                  :store.item.photo/index]}
                             {:store/_items [{:store/profile [:store.profile/name]}]}]}
     :query/current-route
     :query/messages])
  Object
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [featured-items]} (om/props this)]
      ;(debug "Props :" (om/props this))
      (common/page-container
        {:navbar navbar :id "sulo-landing"}
        (photo/cover
          {:photo-id "static/ashim-d-silva-89336"}
          (dom/h1 nil
                  (dom/span nil "Your local marketplace online"))
          (dom/p nil "Shop from and hangout LIVE with your favorite \nlocal vendors and their community"))

        (grid/row-column
          (css/text-align :center)
          (dom/div
            (css/add-class :section-title)
            (dom/h2 nil "Where are you local?"))
          (grid/row
            (grid/columns-in-row {:small 1})
            (grid/column
              nil
              (dom/a
                (css/add-classes [:city-anchor :disabled] {:onClick #(auth/show-lock (shared/by-key this :shared/auth-lock))})

                (photo/photo
                  {:photo-id "s--U-6w3ubU--/v1496874165/static/gabriel-santiago-1595_pepu5p"
                   :transformation :transformation/preview}
                  (photo/overlay
                    nil
                    (dom/div
                      (css/text-align :center)
                      (dom/strong nil "Vancouver"))
                    ;(dom/div
                    ;  (css/add-class :city-photos)
                    ;  (map (fn [i]
                    ;         (photo/product-preview i {:transformation :transformation/thumbnail}
                    ;
                    ;                                (photo/overlay
                    ;                                  nil
                    ;                                  (dom/div
                    ;                                    (css/text-align :center)
                    ;                                    (dom/p nil (dom/span nil "Vancouver"))
                    ;                                    (dom/p nil (dom/small nil "Coming soon. Summer 2017"))))))
                    ;       featured-items))
                    (dom/p (css/add-class :coming-soon) (dom/small nil "Coming soon. Summer 2017")))
                  ))
              )
            (grid/column
              nil
              (dom/a
                (css/add-classes [:city-anchor :disabled] {:onClick #(auth/show-lock (shared/by-key this :shared/auth-lock))})

                (photo/photo
                  {:photo-id "s--1wUD_bGi--/v1496873909/static/alex-shutin-228917"
                   :transformation :transformation/preview}
                  (photo/overlay
                    nil
                    (dom/div
                      (css/text-align :center)
                      (dom/strong nil "Toronto"))
                    ;(dom/div
                    ;  (css/add-class :city-photos)
                    ;  (map (fn [i]
                    ;         (photo/product-preview i {:transformation :transformation/thumbnail}
                    ;
                    ;                                (photo/overlay
                    ;                                  nil
                    ;                                  (dom/div
                    ;                                    (css/text-align :center)
                    ;                                    (dom/p nil (dom/span nil "Vancouver"))
                    ;                                    (dom/p nil (dom/small nil "Coming soon. Summer 2017"))))))
                    ;       featured-items))
                    (dom/p (css/add-class :coming-soon) (dom/small nil "Coming soon. Fall 2017")))
                  ))
              ))
          ;(dom/h4 nil "Don't see your location? Let us know where we should go next!")
          )
        (dom/div
          (css/add-class :suggest-location)
          (grid/row-column
            nil
            (dom/h3 nil "Don't see your location? Let us know where we should go next!")
            (dom/div
              (css/add-class :input-container)
              (dom/input {:type "text"
                          :placeholder "Your location"})
              (dom/input {:type "text"
                          :placeholder "Your email"})
              (button/button-small (css/add-class :search) "Submit"))))

        ;(grid/row-column
        ;  (->> (css/text-align :center)
        ;       (css/add-class :sell-on-sulo))
        ;  (dom/div
        ;    (css/add-class :section-title)
        ;    (dom/h2 nil "Sell on SULO"))
        ;
        ;  ;(dom/h4 nil "Don't see your location? Let us know where we should go next!")
        ;  )
        ))))

(router/register-component :landing-page LandingPage)
