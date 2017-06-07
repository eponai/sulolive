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
    [eponai.common.shared :as shared]
    [eponai.client.routes :as routes]
    [eponai.common.ui.icons :as icons]))

(defn top-feature [opts icon title text]
  (grid/column
    (css/add-class :feature-item)
    (grid/row
      (css/add-class :align-middle)
      (grid/column
        (grid/column-size {:small 2 :medium 12})
        icon)
      (grid/column
        nil
        (dom/strong (css/add-class :feature-title) title)
        (dom/p nil text)))))

(defui LandingPage
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     :query/current-route
     {:query/auth [:db/id]}
     :query/messages])
  Object
  (select-locality [this locality]
    #?(:cljs
       (do
         (set! (.-cookie js/document) (str "locality=" locality))
         (let [cookie-string (js/decodeURIComponent (.-cookie js/document))]
           (debug "Got cookie: " cookie-string)))))
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [auth]} (om/props this)]
      (common/page-container
        {:navbar navbar :id "sulo-landing"}
        (photo/cover
          {:photo-id "static/ashim-d-silva-89336"}
          (dom/h1 nil
                  (dom/span nil "Your local marketplace online"))
          (dom/p nil "Shop from and hangout LIVE with your favorite local vendors and their community"))
        (dom/div
          {:classes ["top-features"]}
          (grid/row
            (grid/columns-in-row {:small 1 :medium 3})
            (top-feature
              nil
              (icons/shopping-bag)
              "Shop and Discover"
              "Get lost in a marketplace filled with your local gems.")
            (top-feature
              nil
              (icons/video-camera)
              "Watch, chat and follow"
              "Hang out with your favourite local brands on SULO LIVE.")
            (top-feature
              nil
              (icons/heart)
              "Join the Community"
              "Sign up to follow others and share your faves.")))

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
                (cond->> (css/add-class :city-anchor {:href    (routes/url :index)
                                                      :onClick #(.select-locality this "Vancouver")})
                         (nil? auth)
                         (css/add-class :disabled))

                (photo/photo
                  {:photo-id       "s--U-6w3ubU--/v1496874165/static/gabriel-santiago-1595_pepu5p"
                   :transformation :transformation/preview}
                  (photo/overlay
                    nil
                    (dom/div
                      (css/text-align :center)
                      (dom/strong nil "Vancouver, BC"))
                    (dom/p (css/add-class :coming-soon) (dom/small nil "Coming soon. Summer 2017"))))))
            (grid/column
              nil
              (dom/a
                (css/add-classes [:city-anchor :disabled] {:onClick #(auth/show-lock (shared/by-key this :shared/auth-lock))})

                (photo/photo
                  {:photo-id       "s--1wUD_bGi--/v1496873909/static/alex-shutin-228917"
                   :transformation :transformation/preview}
                  (photo/overlay
                    nil
                    (dom/div
                      (css/text-align :center)
                      (dom/strong nil "Toronto, ON"))
                    (dom/p (css/add-class :coming-soon) (dom/small nil "Coming soon. Fall 2017"))))))
            (grid/column
              (css/add-classes [:suggest-location])
              (photo/cover
                nil
                (dom/h3 nil "Don't see your location? Let us know where we should go next!")
                (dom/div
                  (css/add-class :input-container)
                  (dom/input {:type        "text"
                              :placeholder "Your location"})
                  (dom/input {:type        "text"
                              :placeholder "Your email"})
                  (button/button-small nil "Submit"))))))))))

(router/register-component :landing-page LandingPage)
