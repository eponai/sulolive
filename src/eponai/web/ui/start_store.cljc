(ns eponai.web.ui.start-store
  (:require
    #?(:cljs
       [cljs.spec :as s]
       :clj
        [clojure.spec :as s])
    #?(:cljs
       [eponai.web.utils :as utils])
        [eponai.common.ui.dom :as dom]
        [om.next :as om :refer [defui]]
        [eponai.common.ui.router :as router]
        [eponai.common.ui.common :as common]
        [eponai.common.ui.navbar :as nav]

        [eponai.common.ui.elements.css :as css]
        [eponai.common.ui.elements.grid :as grid]
        [eponai.common.ui.elements.callout :as callout]
        [clojure.spec :as s]
        [eponai.common.ui.elements.input-validate :as validate]
        [eponai.client.parser.message :as msg]
        [taoensso.timbre :refer [debug]]
        [eponai.client.routes :as routes]
        [eponai.web.ui.photo :as photo]
        [eponai.common.mixpanel :as mixpanel]
        [eponai.web.ui.button :as button]))

(def form-inputs
  {:field.store/name    "store.name"
   :field.store/country "store.country"

   :field/brand         "request.brand"
   :field/email         "request.email"
   :field/website       "request.website"
   :field/locality      "request.locality"})

(s/def :field.store/name (s/and string? #(not-empty %)))
(s/def :field.store/country (s/and #(re-matches #"\w{2}" %)))

(s/def :field/store (s/keys :req [:field.store/name
                                  :field.store/country]))

(defn validate
  [spec m & [prefix]]
  (when-let [err (s/explain-data spec m)]
    (let [problems (::s/problems err)
          invalid-paths (map (fn [p]
                               (str prefix (some #(get form-inputs %) p)))
                             (map :path problems))]
      {:explain-data  err
       :invalid-paths invalid-paths})))

(defui StartStore
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/auth [:user/email
                   :user/can-open-store?
                   {:store.owner/_user [{:store/_owners [:db/id
                                                         {:store/profile [:store.profile/name]}]}]}]}
     :query/messages])
  Object
  (start-store [this]
    #?(:cljs
       (let [store-name (utils/input-value-by-id (:field.store/name form-inputs))
             store-country "ca"                             ;(utils/input-value-by-id (:field.store/country form-inputs))

             input-map {:field.store/name    store-name
                        :field.store/country "CA"}
             validation (validate :field/store input-map)]
         (when (nil? validation)
           (mixpanel/track "Start store")
           (msg/om-transact! this [(list 'store/create {:name store-name :country store-country})]))

         (om/update-state! this assoc :input-validation validation))))
  (componentDidUpdate [this _ _]
    (let [message (msg/last-message this 'store/create)]
      (when (msg/final? message)
        (if (msg/success? message)
          (let [new-store (msg/message message)]
            (mixpanel/people-set {:store true})
            (routes/set-url! this :store-dashboard {:store-id (:db/id new-store)}))))))

  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [auth]} (om/props this)
          {:keys [input-validation]} (om/get-state this)
          message (msg/last-message this 'store/create)]
      (debug "Current auth: " auth)
      (common/page-container
        {:navbar navbar :id "sulo-start-store"}
        (when (msg/pending? message)
          (common/loading-spinner nil (dom/span nil "Starting store...")))
        ;(common/city-banner this locations)
        (photo/cover
          {:photo-id       "static/coming-soon-sell-bg"
           :transformation :transformation/cover}
          (grid/row-column
            nil
            (if (:user/can-open-store? auth)
              (dom/h1 nil "You are invited to open a SULO store!")
              (dom/h1 nil
                      (dom/span nil "Connect with your community on ")
                      (dom/strong nil "SULO Live")))))

        (if (:user/can-open-store? auth)
          (dom/div
            nil
            (grid/row-column
              (css/text-align :center)
              (dom/div
                (css/add-class :section-title)
                (dom/h2 nil "My SULO store")))
            (grid/row
              (css/align :center)
              (grid/column
                (grid/column-size {:small 12 :medium 8 :large 6})
                (if-let [store (-> auth :store.owner/_user first :store/_owners)]
                  (callout/callout
                    (css/text-align :center)
                    (dom/p nil (dom/label nil (get-in store [:store/profile :store.profile/name])))
                    ;(dom/p nil (dom/span nil "Keep ")
                    ;       (dom/em nil (get-in store [:store/profile :store.profile/name]))
                    ;       (dom/span nil " updated via your store dashboard"))

                    (dom/a (->> (css/button {:href (routes/url :store-dashboard {:store-id (:db/id store)})})
                                (css/add-class :green)
                                (css/add-class :expanded))
                           (dom/span nil (str "Continue to your store")))
                    (dom/p nil
                           (dom/small nil (str "Logged in as " (:user/email auth)))))
                  (callout/callout
                    (css/add-class :start-store-form)
                    (dom/label nil "Store name")
                    (validate/input {:id          (:field.store/name form-inputs)
                                     :type        "text"
                                     :placeholder "My store"}
                                    input-validation)

                    (dom/label nil "Local in")
                    (dom/select {:id           (:field.store/country form-inputs)
                                 :defaultValue "vancouver"}
                                (dom/option {:value "vancouver"} "Vancouver, BC"))
                    (when (some? (:user/email auth))
                      (dom/p (css/text-align :center)
                             (dom/small nil (str "Logged in as " (:user/email auth)))))
                    (dom/a
                      (->> (css/button {:onClick #(.start-store this)})
                           (css/add-class :expanded)) (dom/span nil "Start store")))))))

          (dom/div
            nil
            (grid/row-column
              (css/text-align :center)
              (dom/div
                (css/add-class :section-title)
                (dom/h2 nil "Start a SULO store"))
              (dom/p nil "Want to join this ride with us? Access from us is needed to start a SULO store, provide us with information about your brand and we'll get in touch. We're rolling out invites and would love to have you in the SULO family!"))

            (grid/row
              (css/align :center)
              (grid/column
                (grid/column-size {:small 12 :medium 8 :large 6})
                (callout/callout
                  (css/add-class :request-access-form)
                  (dom/label nil "Name")
                  (dom/input {:type "email" :placeholder "Your brand" :id (:field/brand form-inputs)})
                  (dom/label nil "Email (where we can contact you)")
                  (dom/input {:type "email" :placeholder "youremail@example.com" :id (:field/email form-inputs)})
                  (dom/label nil "Website")
                  (dom/input {:type "text" :placeholder "yourwebsite.com" :id (:field/website form-inputs)})

                  (dom/label nil "Where are you local?")
                  (dom/input {:type "text" :placeholder "e.g. Vancouver" :id (:field/locality form-inputs)})

                  (dom/div
                    (css/add-class :text-center)
                    (button/button
                      nil
                      (dom/span nil "Get me access!"))
                    (dom/p nil
                           (dom/small nil
                                      (dom/span nil "By signing up you accept our ")
                                      (dom/a {:href    "//www.iubenda.com/privacy-policy/8010910"
                                              :classes ["iubenda-nostyle no-brand iubenda-embed"]
                                              :title   "Privacy Policy"}
                                             (dom/span nil "Privacy Policy"))))
                    ))))

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