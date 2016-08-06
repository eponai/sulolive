(ns eponai.mobile.ios.ui.add-transaction
  (:require [clojure.string :as s]
            [datascript.core :as d]
            [eponai.client.ui :as ui :refer-macros [opts camel]]
            [eponai.client.lib.transactions :as lib.t]
            [eponai.mobile.components.fade-in-view :as fade-in]
            [eponai.mobile.components.pick :as p]
            [eponai.mobile.components :as c :refer [view text text-input touchable-highlight picker picker-item date-picker-ios scroll-view activity-indicator-ios picker-ios]]
            [eponai.mobile.components.button :as button]
            [eponai.mobile.components.nav :as nav]
            [eponai.mobile.ios.style :refer [styles]]
            [eponai.common.format :as f]
            [eponai.common.format.date :as date]
            [om.next :as om :refer-macros [defui]]
            [taoensso.timbre :refer-macros [debug error]]))

(def modes #{:edit :create})

(defn attribute-by-key [props query-attr key]
  {:pre [(keyword? query-attr) (keyword? key)]}
  (->> (group-by key (get props query-attr))
       (reduce-kv (fn [m k v]
                    (assoc m k (first v)))
                  {})))

(defn- validate-transaction [t]
  (letfn [(verify-keys-in [ks keys-to-verify]
            (assert (every? (set (keys (get-in t ks))) keys-to-verify)
                    (str "Transaction did not have every required key. Keys: " keys-to-verify
                         "  Transaction:" t)))]
    (when t
      (verify-keys-in [] [:transaction/uuid :transaction/amount :transaction/date
                          :transaction/type :transaction/currency :transaction/project
                          :db/id])
      (verify-keys-in [:transaction/date] [:date/ymd])
      (verify-keys-in [:transaction/currency] [:currency/code])
      (verify-keys-in [:transaction/project] [:project/uuid])
      (verify-keys-in [:transaction/type] [:db/ident]))))

;; TODO: Put common code in client or something?
;; Query and initLocalState copied from web.ui
(defui AddTransactionContent
  static om/IQueryParams
  (params [this]
    {:mutation-uuids []})

  static om/IQuery
  (query [this]
    '[({:query/messages [:tx/mutation-uuid :tx/message :tx/status]} {:mutation-uuids ?mutation-uuids})
      {:query/all-currencies [:currency/code :currency/name]}
      {:query/all-projects [:project/uuid :project/name :project/users]}])
  Object
  (initLocalState [this]
    (let [props (om/props this)
          {:keys [query/all-currencies query/all-projects]} props
          {:keys [transaction]} (om/get-computed this)]
      (validate-transaction transaction)
      {:projects-by-uuid    (attribute-by-key props :query/all-projects :project/uuid)
       :currencies-by-code (attribute-by-key props :query/all-currencies :currency/code)
       :input-tag          {:tag/name ""}
       :input-transaction  (merge
                             {:transaction/amount   "0.00"
                              :transaction/date     (date/date-map (date/today))
                              ;{:date/ymd (-> (js/Date.)
                              ;                                     (f/js-date->utc-ymd-date)
                              ;                                     (f/date->ymd-string))
                              ; }
                              :transaction/tags     #{}
                              :transaction/currency {:currency/code (-> all-currencies first :currency/code)}
                              :transaction/project   {:project/uuid (-> all-projects first :project/uuid)}
                              :transaction/type     {:db/ident :transaction.type/expense}}
                             transaction)}))
  (componentWillUpdate [this next-props _]
    (when (not= (:query/all-projects (om/props this))
                (:query/all-projects next-props))
      (om/update-state! this assoc :projects-by-uuid
                        (attribute-by-key next-props :query/all-projects :project/uuid))
      (om/update-state! this assoc :currencies-by-code
                        (attribute-by-key next-props :query/all-currencies :currency/code))))

  (componentDidUpdate [this prev-props prev-state]
    (let [{:keys [query/messages]} (om/props this)
          {:keys [mutation-uuids]} (om/get-params this)
          message-uuids (map :tx/mutation-uuid messages)]
      ;; When there's a pending mutation-uuid in the messages,
      ;; Remove the pending mutation-uuid.
      (when (and (seq message-uuids) (seq mutation-uuids)
                 (some (set mutation-uuids) message-uuids))
        (om/update-query! this update-in [:params :mutation-uuids]
                         #(into []
                                (remove (set message-uuids))
                                %)))))
  
  (update-transaction! [this k v]
    (om/update-state! this assoc-in [:input-transaction k] v))

  (await-mutation [this mutation-uuid]
    (om/update-query! this update-in [:params :mutation-uuids] conj mutation-uuid))

  (create-transaction! [this]
    (let [mutation-uuid (d/squuid)]
      (.await-mutation this mutation-uuid)
      (om/transact!
       this `[(transaction/create
                ~(-> (:input-transaction (om/get-state this))
                     (assoc :mutation-uuid mutation-uuid)
                     (assoc :transaction/uuid (d/squuid))
                     (assoc :transaction/created-at (.getTime (js/Date.)))
                     (update :transaction/type :db/ident)))
              :proxy/route-data])))

  (edit-transaction! [this]
    (let [mutation-uuid (d/squuid)]
      (let [{:keys [input-transaction]} (om/get-state this)
           {:keys [transaction]} (om/get-computed this)]
       (assert transaction
               (str "Was no :transaction in om/computed when editing transaction: " input-transaction ". We need a :transaction in om/computed when executing action"
                    " transaction/edit."))
       (.await-mutation this mutation-uuid)
       (om/transact!
         this `[(transaction/edit
                  ~(-> input-transaction
                       (lib.t/diff-transaction (:transaction (om/get-computed this)))
                       (assoc :db/id (:db/id transaction))
                       (assoc :transaction/uuid (:transaction/uuid transaction))
                       (assoc :mutation-uuid mutation-uuid)))
                :proxy/route-data]))))

  (render [this]
    (let [{:keys [query/all-currencies query/all-projects query/messages] :as props} (om/props this)
          {:keys [input-tag input-transaction projects-by-uuid currencies-by-code]} (om/get-state this)
          {:keys [transaction/date transaction/tags transaction/currency
                  transaction/project transaction/amount transaction/title
                  transaction/type]} input-transaction
          js-date (date/js-date date)
          computed (.-computed props)
          on-cancel (aget computed "on-cancel")
          on-saved (aget computed "on-save")
          mode (keyword (.-mode computed))
          {:keys [tx.status/success tx.status/error]} (group-by :tx/status messages)
          all-answered (every? (set (map :tx/mutation-uuid messages))
                               (:mutation-uuids (om/get-params this)))]
      (debug "render, Messages:" messages
             " params: " (om/get-params this)
             " success: " success)
      (debug "Add transaction got computed: " computed)
      (assert (contains? modes mode)
              (str "Required om/computed key :mode needs to be one of: " modes " was: " mode " computed: " computed))
      (view (opts {:style {:margin 20
                           :justify-content "space-between"}})
            (scroll-view
              ;;TODO: How much height do we have to play with?
              ;;TODO: Adjust keyboard to the scroll view
              ;{:height 400}
              ;; When there are messages which haven't been replied to yet, show activity indicator.
              (when-not all-answered
                (activity-indicator-ios
                  (camel {:align-self "center"
                          :color "white"})))

              (when (seq success)
                (.alert js/ReactNative.AlertIOS "Success!" (s/join ". " (map :tx/message success))
                        #(when on-saved
                          (on-saved))))
              (when (seq error)
                (.alert js/ReactNative.AlertIOS "Failures" (s/join ". " (map :tx/message error))))

              (text (styles :title)
                    (str (get {:edit "Edit" :create "New"} mode) " "
                         (get {:transaction.type/income  "income"
                               :transaction.type/expense "expense"}
                              (:db/ident type) (str "Unknown type: " type))))
              (view (styles :row)
                    (text (styles :row-text) "Project:")
                    (apply picker
                           (camel {:style           {:width 200}
                                   :selected-value  (str (:project/uuid project))
                                   :item-style      {:width 200 :height 80}
                                   :on-value-change #(.update-transaction! this
                                                                           :transaction/project
                                                                           {:project/uuid %})})
                           (map (fn [{:keys [project/uuid]}]
                                  (picker-item {:label (get-in projects-by-uuid [uuid :project/name] "")
                                                :value (str uuid)}))
                                all-projects)))
              (view (styles :row)
                    (text (styles :row-text) "Title: ")
                    (text-input (styles :row-input
                                        {:on-change-text  #(.update-transaction! this :transaction/title %)
                                         :value           title
                                         :auto-capitalize "none"})))
              (view (styles :row)
                    (text (styles :row-text) "Amount")
                    (text-input (styles :row-input
                                        {:on-change-text  #(.update-transaction! this :transaction/amount %)
                                         :keyboard-type   "number-pad"
                                         :value           (str amount)
                                         :auto-capitalize "none"})))
              ;; TODO: Duplicated code project (sheet), except keys and symbols eg (:currency/code currency)
              (view (styles :row)
                    (text (styles :row-text) "Currency:")
                    (apply picker
                           (camel {:style           {:width 200}
                                   :selected-value  (str (:currency/code currency))
                                   :item-style      {:width 200 :height 80}
                                   :on-value-change #(.update-transaction! this
                                                                           :transaction/currency
                                                                           {:currency/code %})})
                           (map (fn [{:keys [currency/code]}]
                                  (picker-item {:label (get-in currencies-by-code [code :currency/name] "")
                                                :value (str code)}))
                                all-currencies)))
              (view (styles :row)
                    (text (styles :row-text) "Date")
                    (date-picker-ios (camel
                                       {:date           js-date
                                        :mode           "date"
                                        :on-date-change #(.update-transaction! this
                                                                               :transaction/date
                                                                               %)})))
              (view (styles :row)
                    (text (styles :row-text) "Tags")
                    (text-input (styles :row-input
                                        {:on-change-text    #(om/update-state! this
                                                                               assoc-in
                                                                               [:input-tag :tag/name] %)
                                         :on-submit-editing #(when (seq (:tag/name input-tag))
                                                              (om/update-state!
                                                                this
                                                                (fn [state]
                                                                  (-> state
                                                                      (update-in [:input-transaction
                                                                                  :transaction/tags]
                                                                                 conj
                                                                                 input-tag)
                                                                      (assoc-in [:input-tag
                                                                                 :tag/name]
                                                                                "")))))
                                         :keyboard-type     "number-pad"
                                         :value             (:tag/name input-tag)
                                         :auto-capitalize   "none"}))
                    (apply view (camel {:flex-direction "row" :width 200})
                           (ui/map-all tags
                                       #(text (camel {:width 30 :font-size 12})
                                              (:tag/name %))))))
            (button/primary {:title "Save"
                             :on-press #(condp = mode
                                         :create (.create-transaction! this)
                                         :edit (.edit-transaction! this))})))))

(def ->AddTransactionContent (om/factory AddTransactionContent))

(defui AddTransactionForm
  ;static om/IQuery
  ;(query [_]
  ;  [{:query/all-projects [:project/uuid :project/name :project/users]}])
  Object
  ;(initLocalState [_]
  ;  {:selected-project :one})
  (render [this]
    (let [{:keys [selected-project selected-input-field anim]} (om/get-state this)
          props (om/props this)
          all-projects (.-projects props)
          PickerItemIOS (.-Item (.-PickerIOS js/ReactNative))
          AnimatedView js/ReactNative.Animated.View
          animation (or anim (js/ReactNative.Animated.Value. 0))]
      (debug "Got JS props" all-projects)
      (view
        nil
        ;(if (= :input.selected/projects selected-input-field)
        ;  (picker-ios
        ;    (opts {:selectedValue selected-project
        ;           :onValueChange #(om/update-state! this assoc :selected-project (keyword %))})
        ;
        ;    (map (fn [val]
        ;           (c/create-element PickerItemIOS
        ;                             {:key   val
        ;                              :value val
        ;                              :label (name val)}))
        ;         [:one :two :three :four]))
        ;  (button/list-item {:title    (name selected-project)
        ;                     :on-press #(do
        ;                                 (.start (.timing js/ReactNative.Animated animation #js {:toValue 1 :duration 2000}))
        ;                                 (om/update-state! this assoc :selected-input-field :input.selected/projects))}))

        (mapv (fn [p]
               (debug "Mapping over projects: " (.-name p))
                (debug (.-uuid p)))
             all-projects)

        (if (= :input.selected/projects selected-input-field)
          ;(p/expandable
          ;  {:data     [:one :two :three :four]
          ;   :on-press #(om/update-state! this assoc :selected-input-field :input.selected/projects)
          ;             :is-expanded? (= :input.selected/projects selected-input-field)}
          ;  {:selectedValue selected-project
          ;   :onValueChange #(om/update-state! this assoc :selected-project (keyword %))})
          (fade-in/->FadeInView {:children
                                 (picker-ios
                                   (opts {:selectedValue (or selected-project (-> all-projects first .-uuid str))
                                          :onValueChange #(om/update-state! this assoc :selected-project %)})

                                   (map (fn [val]
                                          (c/create-element PickerItemIOS
                                                            {:key   (str (.-uuid val))
                                                             :value (str (.-uuid val))
                                                             :label (.-name val)}))
                                        all-projects))})
          ;(c/create-element AnimatedView
          ;                  (opts {:style {:opacity animation :flex 1}})
          ;                  (picker-ios
          ;                    (opts {:selectedValue selected-project
          ;                           :onValueChange #(om/update-state! this assoc :selected-project (keyword %))})
          ;
          ;                    (map (fn [val]
          ;                           (c/create-element PickerItemIOS
          ;                                             {:key   val
          ;                                              :value val
          ;                                              :label (name val)}))
          ;                         [:one :two :three :four])))
          (let [t (if selected-project
                    (do
                      (debug "Found selected project: " selected-project)
                      (get (group-by (fn [p] (str (.-uuid p))) all-projects) selected-project))
                    (-> all-projects first .-name))]
            (debug "Got title: " t)
            (button/list-item {:title    (if selected-project
                                           (get (group-by (fn [p] (str (.-uuid p))) all-projects) selected-project)
                                           (-> all-projects first .-name))
                               :on-press #(om/update-state! this assoc :selected-input-field :input.selected/projects)})))
        (text nil "This is some new transaction...")))))

(def ->AddTransactionForm (om/factory AddTransactionForm))

(defui AddTransaction
  static om/IQuery
  (query [this]
    '[{:query/all-currencies [:currency/code :currency/name]}
      {:query/all-projects [:project/uuid :project/name :project/users]}])
  Object
  (save [this])
  (render [this]
    (let [{:keys [query/all-currencies query/all-projects]} (om/props this)
          {:keys [on-cancel]} (om/get-computed this)]
      (debug "Got all projects: " all-projects)
      (nav/navigator
        {:initial-route {:title              ""
                         :component          ->AddTransactionForm
                         :onLeftButtonPress  on-cancel
                         :leftButtonTitle    "Cancel"
                         :onRightButtonPress #(.save this)
                         :rightButtonTitle   "Save"
                         :passProps          {:projects all-projects}}}))))

(def ->AddTransaction (om/factory AddTransaction))