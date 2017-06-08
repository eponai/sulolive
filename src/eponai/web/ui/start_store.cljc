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
    [eponai.common.mixpanel :as mixpanel]))

(def form-inputs
  {:field.store/name    "store.name"
   :field.store/country "store.country"})

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
                   {:store.owner/_user [{:store/_owners [:db/id
                                                         {:store/profile [:store.profile/name]}]}]}]}
     :query/messages])
  Object
  (start-store [this]
    #?(:cljs
       (let [store-name (utils/input-value-by-id (:field.store/name form-inputs))
             store-country (utils/input-value-by-id (:field.store/country form-inputs))

             input-map {:field.store/name    store-name
                        :field.store/country store-country}
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
          (common/loading-spinner nil))
        (photo/header
          {:photo-id "static/coming-soon-sell-bg"
           :transformation :transformation/cover}
          (dom/div
            nil
            ;(grid/row-column
            ;  nil
            ;  (dom/h1 nil (dom/span nil "Connect with your community on ")
            ;          (dom/strong nil "SULO Live")))
            (grid/row
              (css/align :center)
              (grid/column
                (grid/column-size {:small 12 :medium 6 :large 8})
                (dom/h1 nil (dom/span nil "Connect with your community on ")
                        (dom/strong nil "SULO Live")))
              (grid/column
                (grid/column-size {:small 12 :medium 6 :large 4})
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

                    (dom/label nil "Country")
                    (dom/select {:id           (:field.store/country form-inputs)
                                 :defaultValue "ca"}
                                (dom/option {:value "ca"} "Canada"))
                    (when (some? (:user/email auth))
                      (dom/p (css/text-align :center)
                             (dom/small nil (str "Logged in as " (:user/email auth)))))
                    (dom/a
                      (->> (css/button {:onClick #(.start-store this)})
                           (css/add-class :green)
                           (css/add-class :expanded)) (dom/span nil "Start store"))))))))
        ))))

(def ->StartStore (om/factory StartStore))

(router/register-component :sell StartStore)