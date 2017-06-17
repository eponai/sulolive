(ns eponai.web.ui.landing-page
  (:require
    [cemerick.url :as url]
    [clojure.spec :as s]
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
    [eponai.common.ui.elements.input-validate :as v]
    [eponai.web.social :as social]
    [eponai.client.utils :as client-utils]
    [eponai.client.parser.message :as msg]))

(def form-inputs
  {:field/email    "field.email"
   :field/location "field.location"})

(s/def :field/email #(client-utils/valid-email? %))
(s/def :field/location (s/and string? #(not-empty %)))
(s/def ::location (s/keys :req [:field/email :field/location]))

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
        (dom/h4 (css/add-class :feature-title) title)
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
  (submit-new-location [this]
    #?(:cljs
       (let [email (web-utils/input-value-by-id (:field/email form-inputs))
             location (web-utils/input-value-by-id (:field/location form-inputs))
             input-map {:field/email email :field/location location}

             validation (v/validate ::location input-map form-inputs)]
         (debug "Input validation: " validation)
         (when (nil? validation)
           (msg/om-transact! this [(list 'location/suggest {:location location :email email})]))
         (om/update-state! this assoc :input-validation validation))))

  (componentDidMount [this]
    (let [{:query/keys [current-route]} (om/props this)]
      #?(:cljs
         (when (= (:route current-route) :landing-page/locality)
           (when-let [locs (web-utils/element-by-id "sulo-locations")]
             (web-utils/scroll-to locs 250))))))

  (componentDidUpdate [this _ _]
    (let [last-message (msg/last-message this 'location/suggest)]
      (when (msg/final? last-message)
        ;(when (msg/success? last-message)
        ;  #?(:cljs
        ;     (do
        ;       (set! (.-value (web-utils/element-by-id (:field/email form-inputs))) "")
        ;       (set! (.-value (web-utils/element-by-id (:field/location form-inputs))) ""))))
        (om/update-state! this assoc :user-message (msg/message last-message)))))
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [auth]} (om/props this)
          {:keys [input-validation user-message]} (om/get-state this)
          last-message (msg/last-message this 'location/suggest)]
      (common/page-container
        {:navbar navbar :id "sulo-landing"}
        (photo/cover
          {:photo-id "static/shop"}
          (dom/div
            (css/add-class :section-title)
            (dom/h1 nil (dom/span nil "Your local marketplace online")))
          (dom/p nil (dom/span nil "Global change starts local. Shop and hang out with your favorite local brands and with each other. It's all LIVE.")))
        (dom/div
          {:classes ["top-features"]}
          (grid/row
            (grid/columns-in-row {:small 1 :medium 3})
            (top-feature
              nil
              (icons/shopping-bag)
              "Shop and discover"
              "Explore and shop from a marketplace filled with your local gems.")
            (top-feature
              nil
              (icons/video-camera)
              "Watch and chat"
              "Watch live streams while you chat with brands and other users on SULO.")
            (top-feature
              nil
              (icons/heart)
              "Join the community"
              "Sign in to follow stores and be the first to know when they are live.")))

        (dom/div
          (css/text-align :center {:id "sulo-locations"})
          (common/content-section
            nil
            "Where are you local?"
            (grid/row
              (->> (grid/columns-in-row {:small 1})
                   (css/add-classes [:locations]))
              (grid/column
                nil
                (dom/a
                  (css/add-class :city-anchor {:onClick #(do
                                                           (debug "Setting locality!")
                                                           (.select-locality this "Vancouver / BC")
                                                           (if (nil? auth)
                                                             (auth/show-lock (shared/by-key this :shared/auth-lock))
                                                             (routes/set-url! this :index nil)))
                                               })
                  (photo/photo
                    {:photo-id       "static/landing-vancouver-2"
                     :transformation :transformation/preview}
                    (photo/overlay
                      nil
                      (dom/div
                        (css/text-align :center)
                        (dom/strong nil "Vancouver / BC")
                        (dom/div
                          {:classes [:button :hollow]}
                          (dom/span nil "Enter")))
                      (when (nil? auth)
                        (dom/p (css/add-class :coming-soon) (dom/small nil "Coming soon - Summer 2017")))))))
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
                  (if (msg/final? last-message)
                    (dom/h3 nil (str user-message))
                    [(dom/h3 nil "Local somewhere else? Subscribe to our newsletter and let us know where we should go next!")
                     (dom/form
                       (css/add-class :input-container)
                       (v/input {:type        "text"
                                 :placeholder "Your location"
                                 :id          (:field/location form-inputs)}
                                input-validation)
                       (v/input {:type        "email"
                                 :placeholder "Your email"
                                 :id          (:field/email form-inputs)}
                                input-validation)
                       (dom/button
                         (css/add-class :button {:onClick #(do
                                                            (.preventDefault %)
                                                            (.submit-new-location this))})
                         (dom/span nil "Submit")))
                     (when (msg/pending? last-message)
                       (dom/p (css/add-class :user-message)
                              (dom/small nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))
                              ))])
                  ))
              )
            "")
          )

        (common/sell-on-sulo this)

        (dom/div
          (css/add-class :instagram-feed)
          (common/content-section
            {:href   (:social/instagram social/profiles)
             :target "_blank"}
            "Follow us"
            (dom/div
              (css/add-class "powr-instagram-feed" {:id "0c4b9f24_1497385671"})
              ;(dom/i {:classes ["fa fa-spinner fa-spin"]} )
              )
            "@sulolive on Instagram"))


        ;<div class="powr-instagram-feed" id="0c4b9f24_1497385671"></div>
        ))))

(router/register-component :landing-page LandingPage)
