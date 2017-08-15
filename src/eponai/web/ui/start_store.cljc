(ns eponai.web.ui.start-store
  (:require
    #?(:cljs
       [cljs.spec.alpha :as s]
       :clj
        [clojure.spec.alpha :as s])
    #?(:cljs
       [eponai.web.utils :as utils])
        [eponai.client.utils :as client-utils]
        [eponai.common.ui.dom :as dom]
        [om.next :as om :refer [defui]]
        [eponai.common.ui.router :as router]
        [eponai.common.ui.common :as common]

        [eponai.common.ui.elements.css :as css]
        [eponai.common.ui.elements.grid :as grid]
        [eponai.common.ui.elements.callout :as callout]
        [clojure.spec.alpha :as s]
        [eponai.common.ui.elements.input-validate :as v]
        [eponai.client.parser.message :as msg]
        [taoensso.timbre :refer [debug]]
        [eponai.client.routes :as routes]
        [eponai.web.ui.photo :as photo]
        [eponai.common.mixpanel :as mixpanel]
        [eponai.web.ui.button :as button]
        [eponai.common :as c]
        [eponai.common.ui.elements.menu :as menu]))

(def form-inputs
  {:field.store/name     "store.name"
   :field.store/country  "store.country"
   :field.store/locality "store.locality"

   :field/brand          "request.brand"
   :field/email          "request.email"
   :field/website        "request.website"
   :field/locality       "request.locality"
   :field/message        "request.message"})

(s/def :field.store/name (s/and string? #(not-empty %)))
(s/def :field.store/country (s/and #(re-matches #"\w{2}" %)))
(s/def :field.store/locality number?)

(s/def :field/website (s/and string? #(not-empty %)))

(s/def :field/store (s/keys :req [:field.store/name
                                  :field.store/country]))

(s/def :field/brand (s/and string? #(not-empty %)))
(s/def :field/locality (s/and string? #(not-empty %)))
(s/def :field/email #(client-utils/valid-email? %))
(s/def :field/request (s/keys :req [
                                    ;:field/brand
                                    :field/email
                                    ;:field/locality
                                    :field/website]))

(defn input-id [parent-id child-id]
  (str parent-id "-" child-id))


(defn render-start-store [component id]
  (let [{:query/keys [auth sulo-localities]} (om/props component)
        {:keys [input-validation]} (om/get-state component)]
    (callout/callout
      (css/add-class :start-store-form {:id id})
      (grid/row
        (css/align :middle)
        (grid/column
          nil
          (dom/label nil "Name")
          (v/input {:id          (:field.store/name form-inputs)
                    :type        "text"
                    :placeholder "Shop name"}
                   input-validation))

        (grid/column
          nil
          (dom/label nil "Location")
          (v/select {:id           (:field.store/locality form-inputs)
                     :defaultValue ""}
                    input-validation
                    (dom/option {:value    ""
                                 :disabled true} "--- Select locality ---")
                    (map (fn [l]
                           (dom/option {:value (:db/id l)} (:sulo-locality/title l)))
                         sulo-localities)))
        (grid/column
          (css/add-class :shrink)
          (dom/div
            (css/text-align :center)
            (button/store-navigation-cta
              {:onClick #(.start-store component id)}
              (dom/span nil "Open my shop")))))
      (grid/row-column
        nil
        (when (some? (:user/email auth))
          (dom/p (css/text-align :center)
                 (dom/small nil (str "Logged in as " (:user/email auth)))))))))

(defn render-has-store [component id]
  (let [{:query/keys [auth]} (om/props component)
        store (-> auth :store.owner/_user first :store/_owners)]
    ;(callout/callout
    ;  (css/text-align :center {:id id}))
    (grid/row
      (css/align :center)
      (grid/column
        (css/add-class :shrink)

        (button/store-navigation-cta
          (css/add-class :expanded {:onClick #(do
                                               ;#?(:cljs
                                               ;   (when (nil? (utils/get-locality))
                                               ;     (utils/set-locality)))
                                               (routes/set-url! component :store-dashboard {:store-id (:db/id store)}))})
          (dom/span nil (str "Continue to " (get-in store [:store/profile :store.profile/name]))))
        (dom/p nil
               (dom/small nil (str "Logged in as " (:user/email auth))))))))

(defn render-access-form [component id]
  (let [request-message (msg/last-message component 'user/request-store-access)
        {:query/keys [auth sulo-localities]} (om/props component)
        {:keys [input-validation]} (om/get-state component)]
    (callout/callout
      (css/add-class :request-access-form {:id id})
      ;(dom/label nil "Name")
      ;(v/input
      ;  {:type "email" :placeholder "Your brand" :id (:field/brand form-inputs)}
      ;  input-validation)
      (dom/label nil "Email")
      (v/input
        {:type "email" :placeholder "youremail@example.com" :id (input-id id (:field/email form-inputs)) :defaultValue (:user/email auth)}
        input-validation)

      ;(dom/label nil "Location")
      ;(v/input
      ;  {:type "text" :placeholder "e.g. Vancouver" :id (:field/locality form-inputs)}
      ;  input-validation)

      (dom/label nil "Website/Social media")
      (v/input {:type "text" :placeholder "yourwebsite.com, Facebook page, Instagram"
                :id   (input-id id (:field/website form-inputs))}
               input-validation)

      (dom/label nil "Message")
      (dom/textarea {:placeholder "Anything else you'd like us to know? (optional)"
                     :id          (input-id id (:field/message form-inputs))})

      (dom/div
        (css/add-class :text-center)
        (dom/p nil
               (dom/small nil
                          (dom/span nil "By submitting you accept our ")
                          (dom/a {:href    "//www.iubenda.com/privacy-policy/8010910"
                                  :classes ["iubenda-nostyle no-brand iubenda-embed"]
                                  :title   "Privacy Policy"}
                                 (dom/span nil "Privacy Policy"))))
        (button/store-navigation-cta
          {:onClick #(.request-access component id)}
          (dom/span nil "I want to go LIVE"))
        (cond (msg/pending? request-message)
              (dom/p nil (dom/small nil (dom/i {:classes ["fa fa-spinner fa-spin"]})))
              (msg/final? request-message)
              (if (msg/success? request-message)
                (dom/p nil (msg/message request-message))))))))

(defn render-input-callout [component id]
  (let [{:query/keys [auth]} (om/props component)
        store  (-> auth :store.owner/_user first :store/_owners)

        ;auth {:user/can-open-store? true
        ;      :user/email "dev@sulo.live"}
        ]
    (cond (some? store)
          (render-has-store component id)

          (:user/can-open-store? auth)
          (callout/callout
            (css/add-class :start-store-form)
            (when (some? (:user/email auth))
              (dom/p (css/text-align :center)
                     (dom/small nil (str "Logged in as " (:user/email auth)))))
            (dom/div
              (css/text-align :center)
              (button/store-navigation-cta
                (->> {:onClick (fn [] #?(:cljs (utils/scroll-to (utils/element-by-id "sulo-start-store") 250)))})
                (dom/span nil "Open my LIVE shop"))))

          ;(render-start-store component id)

          :else
          (render-access-form component id))))

(defn render-header-input-callout [component]
  (let [{:query/keys [auth]} (om/props component)
        store (-> auth :store.owner/_user first :store/_owners)
        id "sell-form-header"

        ;auth {:user/can-open-store? true
        ;      :user/email           "dev@sulo.live"}
        ]
    (cond (some? store)
          (render-has-store component id)

          (:user/can-open-store? auth)
          (render-start-store component id)

          :else
          (button/store-navigation-cta
            (->> {:onClick (fn [] #?(:cljs (utils/scroll-to (utils/element-by-id "request-form") 250)))})
            (dom/span nil "Go LIVE with my brand")))))

(defui StartStore
  static om/IQuery
  (query [_]
    [{:query/auth [:user/email
                   :user/can-open-store?
                   {:store.owner/_user [{:store/_owners [:db/id
                                                         {:store/profile [:store.profile/name]}]}]}]}
     {:query/sulo-localities [:sulo-locality/title
                              :db/id]}
     :query/messages])
  Object
  (start-store [this parent-id]
    #?(:cljs
       (let [store-name (utils/input-value-by-id (input-id parent-id (:field.store/name form-inputs)))
             store-country "ca"                             ;(utils/input-value-by-id (:field.store/country form-inputs))

             locality (c/parse-long-safe (utils/input-value-by-id (input-id parent-id (:field.store/locality form-inputs))))
             input-map {:field.store/name     store-name
                        :field.store/country  "CA"
                        :field.store/locality locality
                        }
             validation (v/validate :field/store input-map form-inputs (str parent-id "-"))]
         (debug "Validation: " validation)
         (when (nil? validation)
           (mixpanel/track "Start store")
           (msg/om-transact! this [(list 'store/create {:name store-name :country store-country :locality locality})]))

         (om/update-state! this assoc :input-validation validation))))
  (request-access [this parent-id]
    #?(:cljs
       (let [
             ;brand (utils/input-value-by-id (input-id parent-id (:field/brand form-inputs)))
             email (utils/input-value-by-id (input-id parent-id (:field/email form-inputs)))
             website (utils/input-value-by-id (input-id parent-id (:field/website form-inputs)))
             ;locality (utils/input-value-by-id (input-id parent-id (:field/locality form-inputs)))
             message (utils/input-value-by-id (input-id parent-id (:field/message form-inputs)))

             input-map {
                        ;:field/brand    brand
                        :field/email    email
                        :field/website  website
                        ;:field/locality locality
                        :field/message  message}
             validation (v/validate :field/request input-map form-inputs (str parent-id "-"))]
         (debug "Validation: " validation)
         (when (nil? validation)
           (mixpanel/track "Request access to store")
           (msg/om-transact! this [(list 'user/request-store-access input-map)]))

         (om/update-state! this assoc :input-validation validation))))
  (componentDidUpdate [this _ _]
    (let [message (msg/last-message this 'store/create)]
      (when (msg/final? message)
        (if (msg/success? message)
          (let [new-store (msg/message message)]
            (mixpanel/people-set {:store true})
            (routes/set-url! this :store-dashboard {:store-id (:db/id new-store)}))))))

  (render [this]
    (let [{:query/keys [auth sulo-localities]} (om/props this)
          {:keys [input-validation]} (om/get-state this)
          message (msg/last-message this 'store/create)

          ]
      (debug "Current auth: " auth)
      (dom/div
        (->> {:id "sulo-start-store"}
             (css/add-class :landing-page))
        (cond (msg/pending? message)
              (common/loading-spinner nil (dom/span nil "Starting store...")))
        (dom/div
          (css/add-class :hero)
          (dom/div
            (css/add-class :hero-background)

            (photo/cover {:photo-id "static/coming-soon-sell-bg"})
            ;(dom/video {:autoPlay true}
            ;           (dom/source {:src "https://a0.muscache.com/airbnb/static/P1-background-vid-compressed-2.mp4"}))
            )
          ;(dom/div
          ;  (css/add-class :hero-content)
          ;  (dom/div
          ;    (css/add-class :va-container {:style {:display "block"}})
          ;    (dom/div
          ;      nil
          ;      (grid/row
          ;        (->> (css/add-class :expanded)
          ;             (css/align :center))
          ;        (grid/column
          ;          (grid/column-size {:small 12 :medium 6 :large 4})
          ;          (dom/h1 (css/show-for-sr) "SULO Live")
          ;          (dom/h2 (css/add-class :jumbo-header) "Your brand LIVE")
          ;          (dom/p (css/add-classes [:jumbo-lead :lead]) "Tell the story of your brand and increase sales via LIVE streams"))
          ;        (grid/column
          ;          (grid/column-size {:small 12 :medium 6 :large 3})
          ;          ;(render-access-form this)
          ;          (render-input-callout this "sell-form-header")
          ;          )))))
          (dom/div
            (css/add-class :hero-content {:style {:padding "8rem 0"}})
            (dom/div
              (css/add-class :va-container)
              (dom/h1 (css/show-for-sr) "SULO Live")
              (dom/h2 (css/add-class :jumbo-header) "Your brand LIVE")
              (dom/p (css/add-classes [:jumbo-lead :lead]) "Tell the story of your brand and increase sales via LIVE streams")
              (render-header-input-callout this)

              ))
          )

        (dom/div
          (->> (css/add-classes [:section :banner :gray])
               (css/text-align :center))
          (grid/row-column
            (css/add-class :section-title)
            (dom/h3 (css/add-classes [:sulo-dark :jumbo-header :banner]) "Promote your brand with LIVE video"))
          (menu/vertical
            (css/text-align :center)
            (dom/li nil (dom/p nil
                               (dom/span nil "Retail sites with video ")
                               (dom/a {:href   "https://www.singlegrain.com/video-marketing/just-stats-science-video-engagement/"
                                       :target "_blank"} (dom/span nil "increase conversion by 30%"))
                               (dom/span nil ".")))
            (dom/li nil (dom/p nil
                               (dom/a {:href   "https://blog.hubspot.com/marketing/top-video-marketing-statistics#sm.0001agz8fnm7qcuby3m2fy7vd2qou"
                                       :target "_blank"} (dom/span nil "70% of marketers"))
                               (dom/span nil " claim video produces more conversions than any other content.")))
            (dom/li nil (dom/p nil
                               (dom/span nil "Viewers spend ")
                               (dom/a {:href   "http://tubularinsights.com/live-video-vod-per-play/#ixzz4JTvPtK7v"
                                       :target "_blank"} (dom/span nil "8X longer with live video"))
                               (dom/span nil " than on-demand.")))))


        (dom/div
          (css/add-classes [:section :banner :blue :has-photo :sulo-banner])
          (grid/row
            (->> (grid/columns-in-row {:small 1 :medium 2})
                 (css/align :bottom))
            (grid/column
              nil
              (dom/img {:src "/assets/img/storefront-ss.jpg"}))
            (grid/column
              nil
              (dom/div
                (css/add-class :section-title)
                (dom/h6 nil (dom/strong nil "Your brand on SULO Live"))
                (dom/h3 (css/add-classes [:sulo-dark :jumbo-header :banner]) "Show. Tell. Connect. Sell."))
              (menu/vertical
                nil
                (dom/li nil
                        (dom/h5 nil "Show and tell")
                        (dom/p nil

                               (dom/span nil "Tell your story and demonstrate products via LIVE video on your shop. Unlimited time, unlimited viewers.")))
                (dom/li nil
                        (dom/h5 nil "Interact and connect")
                        (dom/p nil

                               (dom/span nil "Your viewers can ask questions and interact with you and each other via a LIVE chat room.")))
                (dom/li nil
                        (dom/h5 nil "Sell")
                        (dom/p nil

                               (dom/span nil "Your LIVE content and your products are in one place, so your viewers can easily make purchases while they watch your video.")))))
            ))

        (dom/div
          (->> (css/add-classes [:section :banner])
               (css/text-align :center))
          (grid/row-column
            (css/add-class :section-title)
            (dom/h3 (css/add-classes [:sulo-dark :jumbo-header :banner]) "You grow. We grow.")


            (dom/p nil
                   (dom/span nil "We only charge a ")
                   (dom/em nil "20% commission")
                   (dom/span nil " on your listing price and ")
                   (dom/em nil "$0.30 + 2.9% transaction fee")
                   (dom/span nil " on your sales.")
                   )
            (dom/p nil "You pay nothing upfront and there's no hidden fees.")
            ;(dom/h6 nil "On SULO you'll get:")
            ;(menu/vertical
            ;  nil
            ;  (dom/li nil (dom/p nil "Unlimited streaming"))
            ;  (dom/li nil (dom/p nil "Unlimited viewers"))
            ;  (dom/li nil (dom/p nil "Unlimited products"))
            ;  (dom/li nil (dom/p nil "Unlimited chat messages")))

            ))

        (dom/div
          (->> (css/add-classes [:section :blue :banner])
               (css/text-align :center))
          (grid/row
            nil
            (grid/column
              (css/add-class :section-title)
              (dom/h3 (css/add-classes [:sulo-dark :jumbo-header :banner]) "Use any camera."))
            (grid/column
              nil)))


        (dom/div
          (css/add-classes [:hero :hero-footer])

          (dom/div
            (css/add-class :hero-background)
            (photo/cover {:photo-id "static/products"}))
          (dom/div
            (css/add-class :hero-content)
            (dom/div
              (css/add-class :va-container {:style {:display "block"}})




              (grid/row
                (->> {:id "request-form"}
                     (css/add-class :expanded)
                     (css/align :center)
                     (css/align :middle))
                (grid/column
                  (grid/column-size {:small 12 :medium 6 :large 4})
                  (dom/div
                    (css/add-class :section-title)
                    (dom/h2 (css/add-classes [:jumbo-header]) "Go LIVE with your brand"))
                  (dom/p (css/add-classes [:jumbo-lead :lead]) "Are you ready to go LIVE? We're ready to have you onboard!"))


                (grid/column
                  (grid/column-size {:small 12 :medium 6 :large 3})
                  (render-input-callout this "sell-form-footer")

                  )))))))))

(router/register-component :sell StartStore)