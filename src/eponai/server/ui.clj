(ns eponai.server.ui
  (:require
    [om.next :as om]
    [om.dom :as dom]
    [datascript.core :as datascript]
    [eponai.server.ui.html :as html]
    [eponai.common.database :as db]
    [eponai.client.parser.merge :as merge]
    [eponai.client.parser.mutate]
    [eponai.client.parser.read]
    [eponai.client.utils :as client.utils]
    [eponai.common.parser :as parser]
    [eponai.common.parser.util :as parser.util]
    [eponai.common.ui.store :as common.store]
    [eponai.server.auth :as auth]
    [eponai.server.ui.goods :as goods]
    [eponai.server.ui.product :as product]
    [eponai.server.ui.index :as index]
    [eponai.server.ui.store :as store]
    [eponai.server.ui.checkout :as checkout]
    [eponai.server.ui.streams :as streams]
    [eponai.server.ui.common :as common]
    [taoensso.timbre :as timbre :refer [debug]]))

(defn server-send [server-env reconciler-atom]
  (fn [queries cb]
    (run! (fn [[remote-key query]]
            (let [res ((parser/server-parser) server-env query)]
              (cb {:db (db/db (om/app-state @reconciler-atom)) :result res})))
          queries)))

(defn make-reconciler [request-env component]
  (let [reconciler-atom (atom nil)
        parser (parser/client-parser)
        parser (fn [env query & [target]]
                 (parser (merge env (dissoc request-env :state)) query target))
        remotes [:remote :remote/user]
        send-fn (server-send request-env reconciler-atom )
        reconciler (om/reconciler {:state   (datascript/conn-from-db (:empty-datascript-db request-env))
                                   :parser  parser
                                   :remotes remotes
                                   :send    send-fn
                                   :merge   (merge/merge!)
                                   :migrate nil})]
    (reset! reconciler-atom reconciler)
    (client.utils/init-state! reconciler remotes send-fn parser component)
    reconciler))

(defn render-page [{:keys [release?] :as env} component]
  (let [reconciler (make-reconciler env component)
        ui-root (om/add-root! reconciler component nil)
        html-string (dom/render-to-str ui-root)]
    (html/render-html-without-reactid-tags
      (dom/html
       {:lang "en"}
       (apply dom/head nil (common/head release?))
       (dom/body
         nil
         (dom/div
           {:id "sulo-store" :className "sulo-page"}
           ;; Include the server side rendered react html, with react id tags:
           (html/raw-string html-string))

         (common/red5pro-script-tags release?)
         (common/auth0-lock-passwordless release?)
         (dom/script {:src  (common/budget-js-path release?)
                      :type common/text-javascript})

         (common/inline-javascript ["env.web.main.runstore()"])
         ))))
  )

(defn with-doctype [html-str]
  (str "<!DOCTYPE html>" html-str))

(defn render-to-str [component props]
  {:pre [(some? (:release? props))]}
  (with-doctype (dom/render-to-str ((om/factory component) props))))

(defn makesite [component]
  (fn [props]
    (let [component-props ((::component->props-fn props) component)
          ret (render-to-str component (merge component-props
                                              (dissoc props ::component->props-fn)))]
      ret)))

(def auth-html (makesite auth/Auth))
(def goods-html (makesite goods/Goods))
(def product-html (makesite product/Product))
(def index-html (makesite index/Index))
(def store-html (makesite store/Store))
(def checkout-html (makesite checkout/Checkout))
(def streams-html (makesite streams/Streams))

(defn new-store-html [env]
  (debug "RENDERING NEW STORE")
  (parser.util/timeit "old-store" (store-html env))
  (parser.util/timeit "new-store" (timbre/with-level
                                    :info
                                    (render-page env common.store/Store))))