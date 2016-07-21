(ns eponai.mobile.ios.ui.landing
  (:require [om.next :as om :refer-macros [defui]]
            [eponai.mobile.components :refer [view text text-input image list-view touchable-highlight navigator-ios]]
            [eponai.client.ui :refer-macros [opts]]
            [goog.object :as gobj]
            [taoensso.timbre :refer-macros [info debug error trace]]))

(def logo-img (js/require "./images/cljs.png"))

(def style
  {:scene      {:margin 20 :margin-top 40 :flex 1 :justify-content "space-between"}
   :text-input {:height 40 :border-color :gray :border-width 1 :margin-bottom 20 :padding 10}
   :header     {:font-size 30 :font-weight "100" :margin-bottom 20 :text-align "center"}})

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
  (onForward [this]
    (let [props (om/props this)
          nav (.-navigator props)
          comp ->Login]
      (debug "Props on forward: " nav " component: " comp)
      (.push nav #js {:title "Next"
                      :component comp
                      :passProps #js {:myProp "foo"}})))

  (onBack [this]
    (let [props (om/props this)
          nav (.-navigator props)]
      (debug "Props on forward: " nav)
      (.pop nav)))
  (render [this]
    (let [{:keys [:input/email on-change-text]} (om/get-state this)
          {:keys [title on-forward]} (om/props this)]
      (debug "Login: " email " with props " (om/props this))
      (view (opts {:style     (:scene style)})
            (text (opts {:style (:header style)}) "Log in")
            ;(image (opts {:source logo-img
            ;              :style  {:width 80 :height 80 :margin-bottom 30}}))
            (view (:input-container style)
                  (text nil "Email:")
                  (text-input (opts {:style          (:text-input style)
                                     :onChangeText   on-change-text
                                     :placeholder    "youremail@example.com"
                                     :value          (or email "")
                                     :autoCapitalize "none"})))
            (touchable-highlight (opts {:style   {:background-color "#999" :padding 10 :border-radius 5}
                                        :onPress #(.onForward this)
                                        ;:onPress #(om/transact! this `[(signup/email ~{:input-email email
                                        ;                                               :device      :ios})])
                                        })
                                 (text (opts {:style {:color "white" :text-align "center" :font-weight "bold"}})
                                       "Log in"))))))
(def ->Login (om/factory Login))

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
    (navigator-ios {:initialRoute {:title "First" :component ->Login}
                    :style {:flex 1}
                    :translucent true
                    :barTintColor "rgba(0,0,0,0)"
                    :shadowHidden true})))



(def ->LoginMenu (om/factory LoginNavScene))
