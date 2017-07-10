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
    [eponai.common :as c]))

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
(s/def :field/request (s/keys :req [:field/brand
                                    :field/email
                                    :field/locality
                                    :field/website]))
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
  (start-store [this]
    #?(:cljs
       (let [store-name (utils/input-value-by-id (:field.store/name form-inputs))
             store-country "ca"                             ;(utils/input-value-by-id (:field.store/country form-inputs))

             locality (c/parse-long-safe (utils/input-value-by-id (:field.store/locality form-inputs)))
             input-map {:field.store/name     store-name
                        :field.store/country  "CA"
                        :field.store/locality locality}
             validation (v/validate :field/store input-map form-inputs)]
         (when (nil? validation)
           (mixpanel/track "Start store")
           (msg/om-transact! this [(list 'store/create {:name store-name :country store-country :locality locality})]))

         (om/update-state! this assoc :input-validation validation))))
  (request-access [this]
    #?(:cljs
       (let [brand (utils/input-value-by-id (:field/brand form-inputs))
             email (utils/input-value-by-id (:field/email form-inputs))
             website (utils/input-value-by-id (:field/website form-inputs))
             locality (utils/input-value-by-id (:field/locality form-inputs))
             message (utils/input-value-by-id (:field/message form-inputs))

             input-map {:field/brand    brand
                        :field/email    email
                        :field/website  website
                        :field/locality locality
                        :field/message  message}
             validation (v/validate :field/request input-map form-inputs)]
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
          request-message (msg/last-message this 'user/request-store-access)
          store (-> auth :store.owner/_user first :store/_owners)]
      (debug "Current auth: " auth)
      (dom/div
        {:id "sulo-start-store"}
        (cond (msg/pending? message)
              (common/loading-spinner nil (dom/span nil "Starting store...")))
        ;(common/city-banner this locations)
        (photo/cover
          {:photo-id       "static/coming-soon-sell-bg"
           :transformation :transformation/cover}
          (grid/row-column
            nil
            (if (and (:user/can-open-store? auth)
                     (nil? store))
              (dom/h1 nil "You are invited to open a SULO store!")
              (dom/h1 nil "Connect with your community on SULO Live"))))

        (if (or (:user/can-open-store? auth)
                (some? store))
          (dom/div
            nil
            (grid/row-column
              (css/text-align :center)
              (dom/div
                (css/add-class :section-title)
                (if (some? store)
                  (dom/h2 nil (get-in store [:store/profile :store.profile/name]))
                  (dom/h2 nil "Start your store"))))
            (grid/row
              (css/align :center)
              (grid/column
                (grid/column-size {:small 12 :medium 8 :large 6})
                (if (some? store)
                  (callout/callout
                    (css/text-align :center)
                    ;(dom/p nil (dom/label nil (get-in store [:store/profile :store.profile/name])))
                    ;(dom/p nil (dom/span nil "Keep ")
                    ;       (dom/em nil (get-in store [:store/profile :store.profile/name]))
                    ;       (dom/span nil " updated via your store dashboard"))

                    (button/store-navigation-cta
                      (css/add-class :expanded {:onClick #(do
                                                           ;#?(:cljs
                                                           ;   (when (nil? (utils/get-locality))
                                                           ;     (utils/set-locality)))
                                                           (routes/set-url! this :store-dashboard {:store-id (:db/id store)}))})
                      (dom/span nil (str "Continue to " (get-in store [:store/profile :store.profile/name]))))
                    (dom/p nil
                           (dom/small nil (str "Logged in as " (:user/email auth)))))
                  (callout/callout
                    (css/add-class :start-store-form)
                    (dom/label nil "Store name")
                    (v/input {:id          (:field.store/name form-inputs)
                              :type        "text"
                              :placeholder "My store"}
                             input-validation)

                    (dom/label nil "Local in")
                    (v/select {:id           (:field.store/locality form-inputs)
                               :defaultValue ""}
                              input-validation
                              (dom/option {:value    ""
                                           :disabled true} "Select locality")
                              (map (fn [l]
                                     (dom/option {:value (:db/id l)} (:sulo-locality/title l)))
                                   sulo-localities))
                    (when (some? (:user/email auth))
                      (dom/p (css/text-align :center)
                             (dom/small nil (str "Logged in as " (:user/email auth)))))
                    (dom/div
                      (css/text-align :center)
                      (button/store-navigation-cta
                        {:onClick #(.start-store this)}
                        (dom/span nil "Start store"))))))))

          (dom/div
            nil
            (grid/row-column
              (css/text-align :center)
              (dom/div
                (css/add-class :section-title)
                (dom/h2 nil "Request access to start your store"))
              (dom/p nil "Want to join this ride with us? Access from us is needed to start a SULO store for now. Please provide us with information about your business and we'll be in touch with you shortly!"))

            (grid/row
              (css/align :center)
              (grid/column
                (grid/column-size {:small 12 :medium 8 :large 6})
                (callout/callout
                  (css/add-class :request-access-form)
                  (dom/label nil "Name")
                  (v/input
                    {:type "email" :placeholder "Your brand" :id (:field/brand form-inputs)}
                    input-validation)
                  (dom/label nil "Email")
                  (v/input
                    {:type "email" :placeholder "youremail@example.com" :id (:field/email form-inputs) :defaultValue (:user/email auth)}
                    input-validation)

                  (dom/label nil "Where are you local?")
                  (v/input
                    {:type "text" :placeholder "e.g. Vancouver" :id (:field/locality form-inputs)}
                    input-validation)

                  (dom/label nil "Website/Social media")
                  (v/input {:type "text" :placeholder "yourwebsite.com, Facebook page, Instagram" :id (:field/website form-inputs)}
                           input-validation)

                  (dom/label nil "Message")
                  (dom/textarea {:placeholder "Anything else you'd like us to know? (optional)"
                                 :id          (:field/message form-inputs)})

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
                      {:onClick #(.request-access this)}
                      (dom/span nil "Get me access!"))
                    (cond (msg/pending? request-message)
                          (dom/p nil (dom/small nil (dom/i {:classes ["fa fa-spinner fa-spin"]})))
                          (msg/final? request-message)
                          (if (msg/success? request-message)
                            (callout/callout-small
                              (css/add-class :success)
                              (dom/p (css/add-class :success-message) (msg/message request-message)))))))))

            ;(grid/row
            ;  (css/align :middle)
            ;  (grid/column
            ;    (grid/column-size {:small 12 :medium 2})
            ;    (dom/label nil "Name"))
            ;  (grid/column
            ;    nil
            ;    (dom/input {:type "email" :placeholder "Your Brand" :id "beta-NAME"})))
            ;(grid/row
            ;  (css/align :middle)
            ;  (grid/column
            ;    (grid/column-size {:small 12 :medium 2})
            ;    (dom/label nil "Email"))
            ;  (grid/column
            ;    nil
            ;    (dom/input {:type "email" :placeholder "youremail@example.com" :id "beta-EMAIL"})))
            ;(grid/row
            ;  (css/align :middle)
            ;  (grid/column
            ;    (grid/column-size {:small 12 :medium 2})
            ;    (dom/label nil "Website"))
            ;  (grid/column
            ;    nil
            ;    (dom/input {:type "text" :placeholder "yourwebsite.com (optional)" :id "beta-SITE"})))
            ;(grid/row
            ;  (css/align :center)
            ;  (dom/p nil
            ;         (dom/small nil
            ;                    (dom/span nil "By signing up you accept our ")
            ;                    (dom/a {:href    "//www.iubenda.com/privacy-policy/8010910"
            ;                            :classes ["iubenda-nostyle no-brand iubenda-embed"]
            ;                            :title   "Privacy Policy"}
            ;                           (dom/span nil "Privacy Policy")))))
            ;(grid/row
            ;  (css/align :center)
            ;  (dom/button {:classes ["button search"]
            ;               :onClick #(do (.preventDefault %)
            ;                             (when-not (msg/pending? message)
            ;                               (.subscribe this)))}
            ;              (if (msg/pending? message)
            ;                (dom/i {:classes ["fa fa-spinner fa-spin fa-fw"]})
            ;                "Invite me")))
            ;(grid/row
            ;  (css/align :center)
            ;  (dom/p {:classes [(cond
            ;                         (some? client-msg)
            ;                         "text-alert"
            ;                         (msg/final? message)
            ;                         (if (msg/success? message)
            ;                           "text-success"
            ;                           "text-alert"))]}
            ;            (cond
            ;              (not-empty client-msg)
            ;              client-msg
            ;              (msg/final? message)
            ;              (msg/message message)
            ;              :else
            ;              "")))
            ))))))

(router/register-component :sell StartStore)