(ns eponai.web.ui.landing-page
  (:require
    [clojure.spec.alpha :as s]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.router :as router]
    [eponai.common.ui.common :as common]
    [taoensso.timbre :refer [debug]]
    [eponai.web.ui.photo :as photo]
    [eponai.common.ui.elements.grid :as grid]
    #?(:cljs [eponai.web.utils :as web-utils])
    [eponai.client.auth :as auth]
    [eponai.common.shared :as shared]
    [eponai.client.routes :as routes]
    [eponai.common.ui.icons :as icons]
    [eponai.common.ui.elements.input-validate :as v]
    [eponai.web.social :as social]
    [eponai.client.utils :as client-utils]
    [eponai.client.parser.message :as msg]
    [medley.core :as medley]
    [eponai.web.ui.button :as button]
    [eponai.web.ui.content-item :as ci]
    [eponai.common.ui.stream :as stream]))

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
    [:query/current-route
     {:query/auth [:db/id]}
     {:query/sulo-localities [:sulo-locality/title
                              :sulo-locality/path
                              {:sulo-locality/photo [:photo/id]}]}
     {:query/top-streams (om/get-query ci/OnlineChannel)}
     :query/messages])
  Object
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
        (om/update-state! this assoc :user-message (msg/message last-message)))))
  (render [this]
    (let [{:query/keys [auth sulo-localities top-streams]} (om/props this)
          {:keys [input-validation user-message]} (om/get-state this)
          last-message (msg/last-message this 'location/suggest)
          featured-live (first (sort-by #(get-in % [:stream/store :store/visitor-count]) top-streams))]
      (debug "Featured streams: " top-streams)
      (debug "Localitites: " sulo-localities)
      (debug "Featured stream: " featured-live)
      (dom/div
        {:id "sulo-landing"}
        (photo/cover
          {:photo-id "static/products"}
          (grid/row-column
            nil
            (dom/div
              (css/add-class :section-title)
              (dom/h1 nil (dom/span nil "Shop local live"))))
          (dom/div
            (css/add-class :banner)
            (grid/row-column
              (->> (css/text-align :center))
              (dom/p nil (dom/span nil "Global change starts local. Shop and hang out LIVE with your favourite local brands and people from your city."))
              (let [loc-vancouver (medley/find-first #(= "yvr" (:sulo-locality/path %)) sulo-localities)]
                (button/button {:onClick #(.select-locality this loc-vancouver)
                                :classes [:sulo-dark :large]}
                               (dom/span nil (str "Shop from " (:sulo-locality/title loc-vancouver))))))))
        ))))

(router/register-component :landing-page LandingPage)
