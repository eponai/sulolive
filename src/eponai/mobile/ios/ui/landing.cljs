(ns eponai.mobile.ios.ui.landing
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.mobile.components :refer [view text text-input image list-view touchable-highlight navigator-ios]]
    [eponai.mobile.facebook :as fb]
    [goog.object :as gobj]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :refer-macros [info debug error trace]]))


(def logo-img (js/require "./images/world-black.png"))
(def Dimensions (.-Dimensions js/ReactNative))
(def StatusBar (.-StatusBar js/ReactNative))
(def style
  {:scene      {:margin 20 :justify-content "space-between" :flex 1}
   :text-input {:height 40 :border-color :gray :border-width 1 :border-radius 5 :padding 10 :background-color :white :margin-bottom 20}
   :header     {:font-size 30 :font-weight "100" :margin-bottom 20 :text-align "center" :color "#e6e6e6"}})

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

(defui Login
  Object
  (initLocalState [this]
    {:on-change-text #(om/update-state! this assoc :input/email %)})

  (render [this]
    (let [{:keys [:input/email on-change-text]} (om/get-state this)
          {:keys [title on-forward]} (om/props this)
          w (.-width (.get Dimensions "window"))]
      ;(debug "Login: " email " with props " (om/props this))
      (view (opts {:style {:flex 1 :margin 0 :background-color "#01213d"}})
            (image (opts {:source logo-img
                          :style  {:resizeMode "cover" :position "absolute" :tintColor "rgba(255,255,255,0.1)" :width w :align-self "center" :margin-bottom 0}}))
            (view (opts {:style {:flex 1 :margin 20 :justify-content "space-between"}})
                  (view nil)
                  (view nil
                        (text (opts {:style (:header style)}) "Sign in with email")
                        (text-input (opts {:style          (:text-input style)
                                           :onChangeText   on-change-text
                                           :placeholder    "youremail@example.com"
                                           :value          (or email "")
                                           :autoCapitalize "none"}))
                        (touchable-highlight (opts {:style   {:background-color "#999" :padding 10 :border-radius 5 :height 44 :justify-content "center"}
                                                    :onPress #(om/transact! this `[(signup/email ~{:input-email email})])})
                                             (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
                                                   "Email me a link to sign in")))
                  (view nil)
                  (view nil))))))
(def ->Login (om/factory Login))

(defui LoginMenu
  Object
  (onForward [this]
    (let [props (om/props this)
          nav (.-navigator props)
          comp ->Login]
      (debug "Props on forward: " props)
      (.push nav #js {:title ""
                      :component comp
                      :passProps #js {:myProp "foo"}})))
  (onBack [this]
    (let [props (om/props this)
          nav (.-navigator props)]
      ;(debug "Props on forward: " nav)
      (.pop nav)))
  (componentDidMount [this]
    (.setBarStyle StatusBar "light-content"))
  (render [this]
    (let [{:keys [on-login]} (om/props this)
          w (.-width (.get Dimensions "window"))]
      (view (opts {:style {:flex 1 :margin 0 :background-color "#01213d"}})
            (image (opts {:source logo-img
                          :style  {:resizeMode "cover" :position "absolute" :tintColor "rgba(255,255,255,0.5)" :width w :align-self "center" :margin-bottom 0}}))
            (view (opts {:style (:scene style)})

                  ;(view nil
                  ;      (text (opts {:style (:header style)}) "Sign up / Sign in"))

                  ;(text (opts {:style (:header style)}) "JourMoney")
                  (view nil)
                  (view nil
                        (text (opts {:style (:header style)}) "Sign up / Sign in")

                        ;; Facebook login button
                        (touchable-highlight
                          (opts {:style   {:background-color "#4267B2" :padding 10 :border-radius 5 :height 44 :justify-content "center" :margin-vertical 5}
                                 :onPress #(fb/login (fn [{:keys [status] :as res}]
                                                       (debug "Login res: " res)
                                                       (when (and (= status ::fb/login-success)
                                                                  (some? on-login))
                                                         (on-login res))))})

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
                  (view nil))))))

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
