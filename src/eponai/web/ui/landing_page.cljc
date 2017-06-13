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
    #?(:cljs [eponai.web.utils :as web-utils])
    [eponai.web.ui.button :as button]
    [eponai.client.auth :as auth]
    [eponai.common.shared :as shared]
    [eponai.client.routes :as routes]
    [eponai.common.ui.icons :as icons]
    [eponai.web.social :as social]))

(defn top-feature [opts icon title text]
  (grid/column
    (css/add-class :feature-item)
    (grid/row
      (css/add-class :align-middle)
      (grid/column
        (->> (grid/column-size {:small 2 :medium 12})
             (css/text-align :center))
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
  (select-locality [_ locality]
    #?(:cljs
       (web-utils/set-locality locality)))
  (componentDidMount [this]
    (let [{:query/keys [current-route]} (om/props this)]
      #?(:cljs
         (when (= (:route current-route) :landing-page/locality)
           (when-let [locs (web-utils/element-by-id "sulo-locations")]
             (web-utils/scroll-to locs 250))))))
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [auth]} (om/props this)]
      (common/page-container
        {:navbar navbar :id "sulo-landing"}
        (photo/cover
          {:photo-id "static/ashim-d-silva-89336"}
          (dom/h1 nil (dom/span nil "Your local marketplace online"))
          (dom/p nil (dom/span nil "Shop and hang out LIVE with your favorite local brands")))
        (dom/div
          {:classes ["top-features"]}
          (grid/row
            (grid/columns-in-row {:small 1 :medium 3})
            (top-feature
              nil
              (icons/shopping-bag)
              "Shop and discover"
              "Get lost in a marketplace filled with your local gems.")
            (top-feature
              nil
              (icons/video-camera)
              "Watch and chat"
              "Watch live streams from your favorite local brands.")
            (top-feature
              nil
              (icons/heart)
              "Follow and share"
              "Sign up to follow and share your faves.")))

        (grid/row-column
          (css/text-align :center {:id "sulo-locations"})
          (dom/div
            (css/add-class :section-title)
            (dom/h2 nil "Where are you local?"))
          (grid/row
            (->> (grid/columns-in-row {:small 1})
                 (css/add-class :locations))
            (grid/column
              nil
              (dom/a
                (cond->> (css/add-class :city-anchor {:href    (if (some? auth) (routes/url :index) "")
                                                      :onClick #(.select-locality this "Vancouver, BC")})
                         (nil? auth)
                         (css/add-class :inactive))

                (photo/photo
                  {:photo-id       "s--U-6w3ubU--/v1496874165/static/gabriel-santiago-1595_pepu5p"
                   :transformation :transformation/preview}
                  (photo/overlay
                    nil
                    (dom/div
                      (css/text-align :center)
                      (dom/strong nil "Vancouver, BC"))
                    (dom/p (css/add-class :coming-soon) (dom/small nil "Coming soon - Summer 2017"))))))
            ;(grid/column
            ;  nil
            ;  (dom/a
            ;    (css/add-classes [:city-anchor :inactive] nil)
            ;
            ;    (photo/photo
            ;      {:photo-id       "s--1wUD_bGi--/v1496873909/static/alex-shutin-228917"
            ;       :transformation :transformation/preview}
            ;      (photo/overlay
            ;        nil
            ;        (dom/div
            ;          (css/text-align :center)
            ;          (dom/strong nil "Toronto, ON"))
            ;        (dom/p (css/add-class :coming-soon) (dom/small nil "Coming soon - Fall 2017"))))))
            (grid/column
              (css/add-classes [:suggest-location])
              (photo/cover
                nil
                (dom/h3 nil "Local somewhere else? Let us know where we should go next!")
                (dom/div
                  (css/add-class :input-container)
                  (dom/input {:type        "text"
                              :placeholder "Your location"})
                  (dom/input {:type        "text"
                              :placeholder "Your email"})
                  (button/button nil "Submit"))))))
        (common/sell-on-sulo this)
        (dom/div
          (css/add-class :instagram-feed)
          (common/content-section
            {:href   (:social/instagram social/profiles)
             :target "_blank"}
            ""
            (dom/div
              (css/add-class "powr-instagram-feed" {:id "0c4b9f24_1497385671"})
              ;(dom/i {:classes ["fa fa-spinner fa-spin"]} )
              )
            "@sulolive on Instagram"))


        ;<div class="powr-instagram-feed" id="0c4b9f24_1497385671"></div>
        ))))

(router/register-component :landing-page LandingPage)
