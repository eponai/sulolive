(ns eponai.web.ui.user.dashboard
  (:require
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.router :as router]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.web.ui.photo :as photo]
    [eponai.client.routes :as routes]
    #?(:cljs
       [eponai.web.utils :as utils])
    #?(:cljs
       [eponai.client.ui.photo-uploader :as pu])
    [eponai.client.parser.message :as msg]))

(def form-inputs {:user.info/name "user.info.name"})

(defn edit-profile-modal [component]
  (let [{:query/keys [auth]} (om/props component)
        {:keys [photo-upload queue-photo]} (om/get-state component)
        user-profile (:user/profile auth)
        on-close #(om/update-state! component dissoc :modal :photo-upload :queue-photo)]
    (common/modal
      {:on-close on-close
       :size     "full"}
      (grid/row-column
        (->> (css/text-align :center)
             (css/add-class :edit-profile-modal))
        (dom/h2 nil "Edit profile")
        (dom/p nil (dom/small nil "Update your information seen by other users on SULO Live."))
        (menu/vertical
          (css/add-class :section-list)
          (menu/item
            nil
            (grid/row
              (css/align :center)
              (grid/column
                (grid/column-size {:small 12 :medium 8})
                ;(if (some? queue)
                ;  (dom/div
                ;    {:classes "upload-photo cover loading"}
                ;    (photo/cover {:src (:src queue)}
                ;                 (photo/overlay nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))))
                ;  (dom/label {:htmlFor "file-cover"
                ;              :classes ["upload-photo cover"]}
                ;             (if (some? upload)
                ;               (photo/cover {:photo-id       (:public_id upload)
                ;                             :transformation :transformation/preview}
                ;                            (photo/overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]})))
                ;               (photo/cover {:photo-id     (:photo/id cover)
                ;                             :placeholder? true}
                ;                            (photo/overlay nil (dom/i {:classes ["fa fa-camera fa-fw"]}))))
                ;             (photo-uploader component "cover")))
                (if (some? queue-photo)
                  (dom/div
                    {:classes ["upload-photo circle loading user-profile-photo"]}
                    (photo/circle {:src (:src queue-photo)}
                                  (photo/overlay nil (dom/i {:classes ["fa fa-spinner fa-spin"]}))))
                  (dom/label {:htmlFor "file-profile"
                              :classes ["upload-photo circle"]}
                             (if (some? photo-upload)
                               (photo/circle {:photo-id       (:public_id photo-upload)
                                              :transformation :transformation/thumbnail})
                               (photo/user-photo auth {:transformation :transformation/thumbnail}))
                             #?(:cljs
                                (pu/->PhotoUploader
                                  (om/computed
                                    {}
                                    {:id              "profile"
                                     :on-photo-queue  (fn [img-result]
                                                        (om/update-state! component assoc :queue-photo {:src img-result}))
                                     :on-photo-upload (fn [photo]
                                                        (om/update-state! component (fn [s]
                                                                                      (-> s
                                                                                          (assoc :photo-upload photo)
                                                                                          (dissoc :queue-photo))))
                                                        ;(msg/om-transact! this [(list 'photo/upload {:photo photo})
                                                        ;                        :query/user])
                                                        )})))))
                (dom/input {:type         "text"
                            :id           (:user.info/name form-inputs)
                            :defaultValue (:user.profile/name user-profile)})
                (dom/p nil (dom/small nil "This is the name that will be used when you hang out in chats.")))))
          )
        (dom/div
          (css/add-class :action-buttons)
          (dom/a
            (->> (css/button-hollow {:onClick on-close})
                 (css/add-class :secondary)
                 (css/add-class :small))
            (dom/span nil "Close"))

          (dom/a
            (->> (css/button {:onClick #(.save-user-info component)})
                 (css/add-class :secondary)
                 (css/add-class :small))
            (dom/span nil "Save")))
        ;(grid/row
        ;  (css/align :center)
        ;  (grid/column
        ;    (grid/column-size {:small 6 :medium 4 :large 3})
        ;
        ;    )
        ;  (grid/column
        ;    (grid/column-size {:small 12 :medium 8 :large 9})
        ;    (dom/div
        ;      nil
        ;      (grid/row
        ;        nil
        ;        (grid/column
        ;          (->> (grid/column-size {:small 12 :medium 3 :large 2})
        ;               (css/text-align :right))
        ;          (dom/label nil "Name"))
        ;        (grid/column
        ;          nil
        ;          (dom/input {:type         "text"
        ;                      :defaultValue (:user.profile/name user-profile)})))
        ;
        ;      ;(my-dom/div
        ;      ;  (css/grid-row)
        ;      ;  (my-dom/div
        ;      ;    (->> (css/grid-column)
        ;      ;         (css/grid-column-size {:small 3 :medium 3 :large 2})
        ;      ;         (css/text-align :right))
        ;      ;    (dom/label nil "Username"))
        ;      ;  (my-dom/div
        ;      ;    (css/grid-column)
        ;      ;    (dom/input #js {:type "text"})))
        ;
        ;      )
        ;    (dom/div
        ;      (css/text-align :right)
        ;      (dom/a (css/button {:onClick #(.save-info component)}) (dom/span nil "Save")))))
        ))))

(def payment-logos
  {"Visa"             "icon-cc-visa"
   "American Express" "icon-cc-amex"
   "MasterCard"       "icon-cc-mastercard"
   "Discover"         "icon-cc-discover"
   "JCB"              "icon-cc-jcb"
   "Diners Club"      "icon-cc-diners"
   "Unknown"          "icon-cc-unknown"})

(defn payment-info-modal [component]
  (let [{:query/keys [stripe-customer]} (om/props component)
        cards (:stripe/sources stripe-customer)             ;[{:brand "American Express" :last4 1234 :exp-year 2018 :exp-month 4}]
        on-close #(om/update-state! component dissoc :modal)]
    (common/modal
      {:on-close on-close
       :size     :full}
      (grid/row-column
        (css/text-align :center)
        (dom/h2 nil "Credit cards")
        (if (empty? cards)
          (dom/p nil
                 (dom/span nil "You don't have any saved credit cards.")
                 (dom/br nil)
                 (dom/small nil "Save your cards at checkout."))
          (dom/div
            nil
            (menu/vertical
              (css/add-class :section-list)
              (map-indexed (fn [i c]
                             (let [{:stripe.card/keys [brand last4]} c]
                               (menu/item
                                 (css/add-class :section-list-item--card)
                                 (grid/row
                                   (css/add-class :collapse)
                                   ;(grid/column
                                   ;  (css/add-class :shrink)
                                   ;  (dom/div {:classes ["icon" (get payment-logos brand "icon-cc-unknown")]}))
                                   (grid/column
                                     (->> (grid/column-size {:small 12 :medium 4}))
                                     (dom/div
                                       (css/add-class :payment-card)
                                       (dom/div {:classes ["icon" (get payment-logos brand "icon-cc-unknown")]})
                                       (dom/p nil
                                              (dom/span (css/add-class :payment-brand) brand))))
                                   (grid/column
                                     (->> (css/text-align :center)
                                          (grid/column-size {:small 6 :medium 4}))
                                     (dom/small (css/add-class :payment-past4) (str "ending in " last4)))
                                   (grid/column
                                     (->> (css/text-align :right)
                                          (grid/column-size {:small 6 :medium 4}))
                                     (dom/a (->> (css/button-hollow)
                                                 (css/add-class :secondary)
                                                 (css/add-class :small))
                                            (dom/span nil "Set default"))
                                     (dom/a (->> (css/button-hollow)
                                                 (css/add-class :secondary)
                                                 (css/add-class :small))
                                            (dom/span nil "Remove"))))
                                 )))
                           cards))
            (dom/p nil (dom/small nil "Save your cards at checkout."))))
        (dom/a
          (->> {:onClick on-close}
               (css/button-hollow)
               (css/add-class :secondary)
               (css/add-class :small)) (dom/span nil "Close"))))))
(defui UserDashboard
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/auth [:user/email
                   {:user/profile [:user.profile/name
                                   {:user.profile/photo [:photo/id]}]}
                   :user/stripe]}
     {:query/stripe-customer [:db/id :stripe/sources]}
     #?(:cljs
        {:proxy/uploader (om/get-query pu/PhotoUploader)})
     :query/current-route
     :query/messages])
  Object
  (save-user-info [this]
    #?(:cljs
       (let [input-name (utils/input-value-by-id (:user.info/name form-inputs))
             {:keys [photo-upload]} (om/get-state this)]
         (when (not-empty input-name)
           (msg/om-transact! this (cond-> [(list 'user.info/update {:user/name input-name})]
                                          (some? photo-upload)
                                          (conj (list 'photo/upload {:photo photo-upload}))
                                          :else
                                          (conj :query/auth)))))))
  (componentDidUpdate [this _ _]
    (let [info-msg (msg/last-message this 'user.info/update)
          photo-msg (msg/last-message this 'photo/upload)]
      (debug "Messages: " {:info info-msg
                           :photo photo-msg})
      (when (and (msg/final? info-msg)
                 (or (nil? photo-msg) (msg/final? photo-msg)))
        (cond (and (msg/success? info-msg)
                   (or (nil? photo-msg) (msg/success? photo-msg)))
              (do
                (msg/clear-messages! this 'user.info/update)
                (msg/clear-messages! this 'photo/upload)
                (om/update-state! this dissoc :modal))))))

  ;(initLocalState [_]
  ;  {:modal :modal/edit-profile})
  (render [this]
    (let [{:proxy/keys [navbar]
           :query/keys [auth current-route ]} (om/props this)
          {:keys [modal photo-upload queue-photo]} (om/get-state this)
          {user-profile :user/profile} auth
          {:keys [route-params]} current-route]
      (debug "Props: " (om/props this))
      (common/page-container
        {:navbar navbar :id "sulo-user-dashboard"}
        (grid/row-column
          nil
          (dom/h1 nil "Settings")
          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Account"))
          (menu/vertical
            (css/add-class :section-list)
            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/label nil "Email")
                  (dom/span nil (:user/email auth)))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  ;(dom/a
                  ;  (->> (css/button-hollow)
                  ;       (css/add-class :secondary)
                  ;       (css/add-class :small))
                  ;  (dom/span nil "Edit email"))
                  )
                ))
            (cond (= modal :modal/edit-profile)
                  (edit-profile-modal this)
                  (= modal :modal/payment-info)
                  (payment-info-modal this))
            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/label nil "Public profile")
                  (dom/p nil (dom/small nil "This is how other users on SULO will see you when you interact in common spaces (such as store chat rooms)."))
                  )
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/div
                    (css/add-class :user-profile)
                    (dom/span nil (:user.profile/name user-profile))
                    (photo/user-photo auth {:transformation :transformation/thumbnail}))
                  (dom/a
                    (->> {:onClick #(om/update-state! this assoc :modal :modal/edit-profile)}
                         (css/button-hollow)
                         (css/add-class :secondary)
                         (css/add-class :small))
                    (dom/span nil "Edit profile"))))))
          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Shopping details"))
          (menu/vertical
            (css/add-class :section-list)

            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/label nil "Payment info")
                  (dom/p nil (dom/small nil "Manage your saved credit cards. Change your default card or remove old ones.")))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/a
                    (->> {:onClick #(om/update-state! this assoc :modal :modal/payment-info)}
                         (css/button-hollow)
                         (css/add-class :secondary)
                         (css/add-class :small))
                    (dom/span nil "Manage payment info")))))
            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/label nil "Shipping")
                  (dom/p nil (dom/small nil "Your saved shipping addresses for easier checkout.")))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/a
                    (->> (css/button-hollow)
                         (css/add-class :secondary)
                         (css/add-class :small))
                    (dom/span nil "Manage shipping info"))))))

          (dom/div
            (css/add-class :section-title)
            (dom/span nil "Connections"))
          (menu/vertical
            (css/add-class :section-list)
            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/label nil "Facebook")
                  (dom/p nil (dom/small nil "Connect to Facebook to login with your account. We will never post to Facebook or message your friends without your permission")))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/a
                    (->> (css/button)
                         (css/add-class :facebook)
                         (css/add-class :small))
                    (dom/i {:classes ["fa fa-facebook fa-fw"]})
                    (dom/span nil "Connect to Facebook")))))

            (menu/item
              nil
              (grid/row
                (->> (css/align :middle)
                     (css/add-class :collapse))
                (grid/column
                  nil
                  (dom/label nil "Twitter")
                  (dom/p nil (dom/small nil "Connect to Twitter to login with your account. We will never post to Twitter or message your followers without your permission.")))
                (grid/column
                  (grid/column-size {:small 12 :medium 6})
                  (dom/a
                    (->> (css/button)
                         (css/add-class :twitter)
                         (css/add-class :small))
                    (dom/i {:classes ["fa fa-twitter fa-fw"]})
                    (dom/span nil "Connect to Twitter")))))))))))

;(def ->UserSettings (om/factory UserSettings))

(router/register-component :user-settings UserDashboard)