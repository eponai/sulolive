(ns eponai.mobile.navigate
  (:require [om.next :as om :refer [ui]]))

;; Inspired by:
;; https://github.com/amorokh/om-navigate/blob/93255c512111b7aeb448326433deb822d844b3fb/src/om_navigate/navigate.cljs

(def ReactNavigation (js/require "react-navigation"))

(def StackNavigator (.-StackNavigator ReactNavigation))
(def TabNavigator (.-TabNavigator ReactNavigation))
(def DrawerNavigator (.-DrawerNavigator ReactNavigation))

(def TabRouter (.-TabRouter ReactNavigation))

(defn navigate-to
  ([c target] (navigate-to c target {}))
  ([c target params]
   (let [props      (om/props c)
         navigation (:navigation props)]
     (.navigate navigation (name target) (clj->js params)))))

(defn navigate-back
  [c]
  (let [props      (om/props c)
        navigation (:navigation props)]
    (.goBack navigation nil)))

(defn- inst-navigator [nav-comp navigation props]
  (js/React.createElement nav-comp #js {:navigation navigation :screenProps props}))

(defn- create-screen-proxy [screen]
  (om/ui
    static field navigationOptions (.-navigationOptions screen)
    Object
    (render [this]
            (let [screen-factory (om/factory screen)
                  navigation     (.. this -props -navigation)
                  screen-props   (.. this -props -screenProps)
                  props          (assoc screen-props :navigation navigation)]
              (screen-factory props)))))

(defn- create-navigator-proxy [navigator]
  (om/ui
    static field navigator-proxy? true
    static field router (.-router navigator)
    Object
    (shouldComponentUpdate [this _ _] true)
    (render [this]
            (let [navigation   (.. this -props -navigation)
                  screen-props (.. this -props -screenProps)
                  om-props     (om/props this)
                  props        (or om-props screen-props)]
              (inst-navigator navigator navigation props)))))

(defn- transform-routes
  [routes]
  (reduce-kv
    (fn [acc k {:keys [screen] :as v}]
      (assoc acc k
                 (if (.-navigator-proxy? screen)
                   v
                   (assoc v :screen (create-screen-proxy screen)))))
    {}
    routes))

(defn- extract-queries
  [routes]
  (let [screens (map (fn [[_ route]] (:screen route)) routes)]
    (mapcat om/get-query screens)))

(defn- create-navigator
  [routes factory]
  (let [queries   (into [] (extract-queries routes))
        navigator (factory (clj->js (transform-routes routes)))
        proxy     (create-navigator-proxy navigator)]
    (when (seq queries)
      (specify! proxy om/IQuery (query [this] queries)))
    proxy))

(defn create-stack-navigator
  ([routes] (create-stack-navigator routes {}))
  ([routes cfg]
   (create-navigator routes #(StackNavigator % (clj->js cfg)))))

(defn create-tab-navigator
  ([routes] (create-tab-navigator routes {}))
  ([routes cfg]
   (create-navigator routes #(TabNavigator % (clj->js cfg)))))

(defn create-drawer-navigator
  ([routes] (create-drawer-navigator routes {}))
  ([routes cfg]
   (create-navigator routes #(DrawerNavigator % (clj->js cfg)))))

(defn create-custom-navigator
  ([comp router-factory routes] (create-custom-navigator comp router-factory routes {}))
  ([comp router-factory routes cfg]
   (create-navigator
     routes
     (fn [routes']
       (let [router    (router-factory routes' cfg)
             navigator (.createNavigator ReactNavigation router)]
         (.createNavigationContainer ReactNavigation (navigator comp)))))))

(defn create-tab-router
  ([routes] (create-tab-router routes {}))
  ([routes cfg]
   (TabRouter (clj->js routes) (clj->js cfg))))

(defn add-navigation-helpers
  [src]
  (.addNavigationHelpers ReactNavigation (clj->js src)))