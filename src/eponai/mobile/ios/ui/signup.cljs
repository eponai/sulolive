(ns eponai.mobile.ios.ui.signup
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [view text text-input image list-view touchable-highlight navigator-ios tab-bar-ios tab-bar-ios-item]]
    [eponai.mobile.facebook :as fb]
    [eponai.mobile.ios.ui.transactions :as t]
    [goog.object :as gobj]
    [eponai.mobile.ios.ui.tab-bar-item :as tab-bar-item]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [info debug error trace]]))


(def logo-img (js/require "./images/world-black.png"))
(def Dimensions (.-Dimensions js/ReactNative))

(def style
  {:scene      {:margin 20 :justify-content "space-between" :flex 1}
   :text-input {:height 40 :border-color :gray :border-width 1 :border-radius 5 :padding 10 :background-color :white :margin-bottom 20}
   :header     {:font-size 30 :font-weight "300" :margin-bottom 20 :text-align "center" :color "#e6e6e6"}})

(defn map-cover-screen [& body]
  (let [w (.-width (.get Dimensions "window"))]
    (view (opts {:style {:flex 1 :margin 0 :background-color "#01213d"}})
          (image (opts {:source logo-img
                        :style  {:resizeMode "cover" :position "absolute" :tintColor "rgba(255,255,255,0.5)" :width w :align-self "center" :margin-bottom 0}}))

          (apply (partial view (opts {:style (:scene style)})) body))))
(defui Loading
  Object
  (render [this]
    (let [{:keys [ui.component.loading/is-logged-in?]} (om/props this)]
      (view {} (text {} "Loading !")))))

(def ->Loading (om/factory Loading))

(defn- get-prop
  "PRIVATE: Do not use"
  [c k]
  (gobj/get (.-props c) k))

(defui EmailLogin
  Object
  (initLocalState [this]
    {:on-change-text #(om/update-state! this assoc :input/email %)})

  (render [this]
    (let [{:keys [:input/email on-change-text]} (om/get-state this)
          on-email-login (.-onEmailLogin (om/props this))]
      (map-cover-screen
        ;(view (opts {:style {:flex 1 :margin 20 :justify-content "space-between"}}))
        (view nil
              (text (opts {:style (:header style)}) "Sign in with email"))
        (view nil
              (text-input (opts {:style          (:text-input style)
                                 :onChangeText   on-change-text
                                 :placeholder    "youremail@example.com"
                                 :value          (or email "")
                                 :autoCapitalize "none"}))
              (touchable-highlight (opts {:style   {:background-color "#044e8a" :padding 10 :border-radius 5 :height 44 :justify-content "center"}
                                          :onPress #(on-email-login {:input-email email})})
                                   (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
                                         "Email me a link to sign in")))
        (view nil)
        (view nil)))))
(def ->Login (om/factory EmailLogin))

(defui ActivateAccount
  static om/IQuery
  ;(query [_]
  ;  [{:query/auth [{:ui.singleton.auth/user [:user/email]}]}])
  Object
  (initLocalState [this]
    {:on-change-text #(om/update-state! this assoc :input/email %)})
  (render [this]
    (let [on-logout (.-onLogout (om/props this))
          on-activate (.-onActivate (om/props this))
          ;email (.. (om/props this) -user -email)
          user (.. (om/props this) -user)
          {:keys [on-change-text]} (om/get-state this)]
      (debug "User: " (om/props this))
      (map-cover-screen
        (view (opts {:style {:flex 1 :margin-bottom 60 :justify-content "space-between"}})
              (view
                nil
                (text (opts {:style (:header style)}) "Create Account"))
              (view
                nil
                (text-input (opts {:style          (:text-input style)
                                   :onChangeText   on-change-text
                                   :placeholder    "youremail@example.com"
                                   :value          (or (:user/email user) "")
                                   :autoCapitalize "none"}))
                (text-input (opts {:style          (:text-input style)
                                   :onChangeText   on-change-text
                                   :placeholder    "Name"
                                   :value          ""
                                   :autoCapitalize "none"}))
                (touchable-highlight
                  (opts {:style {:background-color "#044e8a" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}})
                  (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}
                               :onPress #(on-activate {:user-uuid (str (:user/uuid user))
                                                       :user-email (:user/email user)})})
                        "Create My Account")))


              (touchable-highlight
                (opts {:style   {:background-color "#999" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
                       :onPress on-logout})

                (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
                      "Sign Out")))))))

(def ->ActivateAccount (om/factory ActivateAccount))

(defui LoginMenu
  Object
  (onForward [this]
    (let [props (om/props this)
          nav (.-navigator props)
          comp ->Login]
      (.push nav #js {:title ""
                      :component comp
                      :passProps props})))
  (onBack [this]
    (let [props (om/props this)
          nav (.-navigator props)]
      (.pop nav)))
  (componentDidMount [this]
    (.setBarStyle (.-StatusBar js/ReactNative) "light-content"))
  (render [this]
      (let [on-facebook-login (.-onFacebookLogin (om/props this))]
        (map-cover-screen
          (view nil
                (text (opts {:style (:header style)}) "Sign up / Sign in"))
          (view nil
                ;; Facebook login button
                (touchable-highlight
                  (opts {:style   {:background-color "#4267B2" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
                         :onPress #(fb/login (fn [{:keys [status] :as res}]
                                               (debug "Login res: " res)
                                               (debug "on-login " (om/props this))
                                               (when (and (= status ::fb/login-success)
                                                          (some? on-facebook-login))
                                                 (on-facebook-login res))))})

                  (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
                        "Continue with Facebook"))
                ;(.createElement js/React
                ;                fbLoginButton
                ;                (clj->js (opts {:style {:height 44} :readPermissions ["email"]})))
                ;(touchable-highlight (opts {:style   {:background-color "#4099FF" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
                ;                            :onPress #(.onForward this)})
                ;                     (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
                ;                           "Twitter"))
                ;(touchable-highlight (opts {:style   {:background-color "#d34836" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
                ;                            :onPress #(.onForward this)})
                ;                     (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
                ;                           "Google+"))
                (text (opts {:style {:text-align "center" :margin-vertical 10 :color "#e6e6e6"}}) "or")
                (touchable-highlight (opts {:style   {:background-color "#999" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
                                            :onPress #(.onForward this)})
                                     (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
                                           "Sign up or sign in with Email")))
          (view nil)
          (view nil))
      ;(view (opts {:style {:flex 1 :margin 0 :background-color "#01213d"}})
      ;      (image (opts {:source logo-img
      ;                    :style  {:resizeMode "cover" :position "absolute" :tintColor "rgba(255,255,255,0.5)" :width w :align-self "center" :margin-bottom 0}}))
      ;      (view (opts {:style (:scene style)})
      ;
      ;            ;(view nil
      ;            ;      (text (opts {:style (:header style)}) "Sign up / Sign in"))
      ;
      ;            ;(text (opts {:style (:header style)}) "JourMoney")
      ;            (view nil (text nil (str "Logged in: ")))
      ;            (view nil
      ;                  (text (opts {:style (:header style)}) "Sign up / Sign in")
      ;
      ;                  ;; Facebook login button
      ;                  (touchable-highlight
      ;                    (opts {:style   {:background-color "#4267B2" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
      ;                           :onPress #(fb/login (fn [{:keys [status] :as res}]
      ;                                                 (debug "Login res: " res)
      ;                                                 (debug "on-login " (om/props this))
      ;                                                 (when (and (= status ::fb/login-success)
      ;                                                            (some? on-login))
      ;                                                   (on-login res))))})
      ;
      ;                    (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
      ;                          "Continue with Facebook"))
      ;                  ;(.createElement js/React
      ;                  ;                fbLoginButton
      ;                  ;                (clj->js (opts {:style {:height 44} :readPermissions ["email"]})))
      ;                  ;(touchable-highlight (opts {:style   {:background-color "#4099FF" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
      ;                  ;                            :onPress #(.onForward this)})
      ;                  ;                     (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
      ;                  ;                           "Twitter"))
      ;                  ;(touchable-highlight (opts {:style   {:background-color "#d34836" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
      ;                  ;                            :onPress #(.onForward this)})
      ;                  ;                     (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
      ;                  ;                           "Google+"))
      ;                  (text (opts {:style {:text-align "center" :margin-vertical 10 :color "#e6e6e6"}}) "or")
      ;                  (touchable-highlight (opts {:style   {:background-color "#999" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
      ;                                              :onPress #(.onForward this)})
      ;                                       (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
      ;                                             "Sign up or sign in with Email")))
      ;            (view nil)
      ;            (view nil)))
      )))

(def ->LoginMenu (om/factory LoginMenu))

(defui LoginNavScene
  static om/IQuery
  (query [this]
    [{:query/loading [:ui.component.loading/is-logged-in?]}])
  Object

  ;(onForward [this]
  ;  (let [{:keys [navigator]} (om/props this)]
  ;    (.push navigator {:title (str "scene " (inc (.-index route))) :index (inc (.-index route))})))
  ;(renderScene [this route nav]
  ;  (debug "Render first scene: " route " nav " nav)
  ;  (->Login {:title  "First scene"
  ;            :on-forward #(.push nav {:title (str "scene " (inc (.-index route))) :index (inc (.-index route))})}))
  (render [this]
    (navigator-ios {:initialRoute {:title ""
                                   :component ->LoginMenu}
                    :style {:flex 1}
                    :translucent false
                    :barTintColor "#01213d"
                    :shadowHidden true})))

(def ->LoginNavScene (om/factory LoginNavScene))
