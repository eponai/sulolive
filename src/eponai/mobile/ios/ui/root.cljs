(ns eponai.mobile.ios.ui.root
  (:require
    [eponai.mobile.components :refer [view text scroll-view]]
    [eponai.mobile.navigate :as navigate]
    [eponai.mobile.react-helper :as e]
    [om.next :as om :refer [defui]]
    [taoensso.timbre :refer [debug]]))

(def styles {:sample-text {:margin 14}})

(defui SampleText
  Object
  (render [this]
    (let [{:keys [text]} (om/props this)]
      (e/text {:style (:sample-text styles)} text))))

(def sample-text (om/factory SampleText))

(defui MyNavScreen
  Object
  (render [this]
    (let [{:keys [banner]} (om/props this)]
      (e/scroll-view nil
                     (sample-text {:text banner})
                     (e/button {:onPress #(navigate/navigate-to this :Profile {:name "Jane"})
                                :title "Go to a profile screen"})
                     (e/button {:onPress #(navigate/navigate-to this :Photos {:name "Jane"})
                                :title "Go to a photos screen"})
                     (e/button {:onPress #(navigate/navigate-back this)
                                :title "Go back"})))))

(def my-nav-screen (om/factory MyNavScreen))

(defui MyHomeScreen
  static field navigationOptions #js {:title "Welcome"}
  Object
  (render [this]
    (let [{:keys [navigation]} (om/props this)]
      (my-nav-screen {:banner "Home Screen" :navigation navigation}))))

(defui MyPhotosScreen
  static field navigationOptions #js {:title "Photos"}
  Object
  (render [this]
    (let [{:keys [navigation]} (om/props this)
          name                 (.. navigation -state -params -name)]
      (my-nav-screen {:banner (str name "'s Photos") :navigation navigation}))))

(defui MyProfileScreen
  static field navigationOptions
  #js {:header (fn [navigation]
                 (let [name  (.. navigation -state -params -name)
                       mode  (.. navigation -state -params -mode)
                       edit? (= mode "edit")]
                   #js {:title (str name "'s Profile")
                        :right (e/button {:title (if edit? "Done" "Edit")
                                          :onPress #(.setParams navigation #js {:mode (if edit? "" "edit")})})}))}
  Object
  (render [this]
    (let [{:keys [navigation]} (om/props this)
          name (.. navigation -state -params -name)
          mode (.. navigation -state -params -mode)
          edit? (= mode "edit")
          banner (str name "'s Profile")
          banner (if edit? (str "Now editing " banner) banner)]
      (my-nav-screen {:banner banner :navigation navigation}))))

(def routes
  {:Home {:screen MyHomeScreen}
   :Profile {:screen MyProfileScreen :path "people/:name"}
   :Photos {:screen MyPhotosScreen :path "photos/:name"}})

(def SimpleStack (navigate/create-stack-navigator routes))

(def banner-styles
  {:banner
   {:backgroundColor "#673ab7"
    :flexDirection "row"
    :alignItems "center"
    :padding 16
    :marginTop 20}
   :image
   {:width 36
    :height 36
    :resizeMode "contain"
    :tintColor "#fff"
    :margin 8}
   :title
   {:fontSize 18
    :fontWeight "200"
    :color "#fff"
    :margin 8}})

(def logo-img (js/require "./images/cljs.png"))

(defui Banner
  Object
  (render [this]
    (e/view {:style (:banner banner-styles)}
            (e/image {:source logo-img :style (:image banner-styles)})
            (e/text {:style (:title banner-styles)} "React Navigation Examples"))))

(def ->Banner (om/factory Banner))

(def example-routes {:SimpleStack {:name        "Stack Example"
                                   :description "A card stack"
                                   :screen      SimpleStack}})

(def main-styles {:item        {:backgroundColor   "#fff"
                                :paddingHorizontal 16
                                :paddingVertical   12
                                :borderBottomWidth 1
                                :borderBottomColor "#ddd"}
                  :image       {:width        120
                                :height       120
                                :alignSelf    "center"
                                :marginBottom 20
                                :resizeMode   "contain"}
                  :title       {:fontSize   16
                                :fontWeight "bold"
                                :color      "#444"}
                  :description {:fontSize 13
                                :color    "#999"}})

(defui MainScreen
  static om/IQuery
  (query [this] [:datascript/schema])
  Object
  (render [this]
    (e/scroll-view nil
                   (->Banner)
                   (map
                     (fn [[key {:keys [name description screen]}]]
                       (e/touchable-opacity {:key     key
                                             :onPress #(navigate/navigate-to this key)}
                                            (e/view {:style (:item main-styles)}
                                                    (e/text {:style (:name main-styles)} name)
                                                    (e/text {:style (:description main-styles)} description))))
                     example-routes))))

(def Root (navigate/create-stack-navigator
            (assoc example-routes :Index {:screen MainScreen})
            {:initialRouteName "Index"
             :headerMode       "none"
             :mode             "modal"}))
