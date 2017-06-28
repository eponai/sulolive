(ns eponai.common.parser.util_test
  (:require
    [eponai.common.parser :as parser]
    [eponai.common.parser.util :as u]
    [clojure.test :as test :refer [are is deftest]])
  #?(:clj
     (:import [clojure.lang ExceptionInfo])))

(deftest graph-read-at-basis-t-tests
  (let [graph (u/graph-read-at-basis-t)
        set-many (fn [graph setter-params]
                   (reduce (fn [g {:keys [basis-t params key]}]
                             {:pre [(every? vector? params)]}
                             (u/set-basis-t g (or key :foo) basis-t params))
                           graph
                           setter-params))]
    (is (= nil (u/get-basis-t graph :foo [])))
    (is (= false (u/has-basis-t? graph :foo)))
    (is (= true (u/has-basis-t? (u/set-basis-t graph :foo 1 [])
                                :foo)))

    (test/testing "single set, can get basis t with same or fewer params"
      (are [set-params get-params] (= 47 (-> graph
                                             (u/set-basis-t :foo 47 set-params)
                                             (u/get-basis-t :foo get-params)))
        [] nil
        [] []
        [[:bar 1]] nil
        [[:bar 1]] []
        [[:bar 1]] {:bar 1}
        [[:bar 1]] [[:bar 1]]
        [[:bar 1] [:baz 2]] []
        [[:bar 1] [:baz 2]] {:bar 1}
        [[:bar 1] [:baz 2]] [[:bar 1]]
        [[:bar 1] [:baz 2]] {:bar 1 :baz 2}
        [[:bar 1] [:baz 2]] [[:bar 1] [:baz 2]]
        ;; Setting nil:
        [[:bar nil]] []
        [[:bar nil]] {:bar nil}
        [[:bar nil]] [[:bar nil]]))
    (test/testing "multiple sets, can get basis-t with fewer params iff there's only one path"
      (are [setters get-params basis-t-or-msg]
           (try
             (and (= basis-t-or-msg
                     (-> (set-many graph setters)
                         (u/get-basis-t :foo get-params))))
             (catch #?@(:clj [Exception e] :cljs [ExceptionInfo e])
                    (if (or (nil? basis-t-or-msg) (number? basis-t-or-msg))
                      (throw e)
                      (re-find basis-t-or-msg #?(:cljs (.-message e)
                                                 :clj  (.getMessage e))))))
        [{:basis-t 1 :params [[:bar 1]]}
         {:basis-t 2 :params [[:bar 2]]}]
        []
        #"multiple values"

        [{:basis-t 47 :params [[:bar 1]]}
         {:basis-t 11 :params [[:bar 2]]}]
        {:bar 1}
        47

        [{:basis-t 47 :params [[:bar 1]]}
         {:basis-t 11 :params [[:bar 2]]}]
        {:bar "foo"}
        nil

        ;; Need to set params in the same order, always.
        [{:basis-t 47 :params [[:bar 1] [:baz 1]]}
         {:basis-t 11 :params [[:baz 2]]}]
        {}
        #"order"

        ;; Cannot add a key once a param has been set.
        [{:basis-t 47 :params [[:bar 1]]}
         {:basis-t 11 :params [[:bar 1] [:baz 1]]}]
        {:bar 1 :baz 1}
        #"additional keys"

        ;; Testing nil vals:
        [{:basis-t 47 :params [[:bar nil]]}
         {:basis-t 11 :params [[:bar 1]]}]
        {:bar nil}
        47

        [{:basis-t 47 :params [[:bar nil]]}
         {:basis-t 11 :params [[:bar 1]]}]
        {:bar 1}
        11

        [{:basis-t 47 :params [[:bar nil] [:baz 2]]}
         {:basis-t 11 :params [[:bar 1] [:baz 1]]}]
        {:bar nil}
        47
        ))
    (test/testing "Merging of 2 graphs is the same applying all setters on them"
      (are [setters1 setters2] (= (u/merge-graphs (set-many graph setters1)
                                                  (set-many graph setters2))
                                  (u/merge-graphs (set-many graph setters2)
                                                  (set-many graph setters1))
                                  (set-many graph (concat setters1 setters2))
                                  (set-many graph (concat setters2 setters1)))
        [{:basis-t 1 :params [[:bar 1]]}]
        [{:basis-t 2 :params [[:bar 2]]}]

        [{:basis-t 1 :params [[:bar 1] [:baz 2] [:fisk "lul"]]}]
        [{:basis-t 2 :params [[:bar 1] [:baz 3] [:fisk "lel"]]}]

        [{:key :query/foo :basis-t 1 :params [[:abc 1]]}]
        [{:key :query/bar :basis-t 2 :params [[:xyz 2]]}]
        ))))

(def join-three-asts
  [{:type :join, :dispatch-key :query/store, :key :query/store, :query [:db/id :store/uuid {:store/profile [:store.profile/description :store.profile/name :store.profile/tagline :store.profile/return-policy {:store.profile/cover [:photo/id]} {:store.profile/photo [:photo/path :photo/id]}]} {:store/owners [{:store.owner/user [:user/email]}]} :store/stripe {:store/sections [:db/id :store.section/label]} {:order/_store [:order/items]} {:store/items [:store.item/name :store.item/description :store.item/price :store.item/index {:store.item/section [:db/id :store.section/label]} {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]} :store.item.photo/index]} {:store.item/skus [:db/id :store.item.sku/inventory :store.item.sku/variation]}]} {:stream/_store [:stream/state]} {:store/shipping [:shipping/policy]}], :children [{:type :prop, :dispatch-key :db/id, :key :db/id} {:type :prop, :dispatch-key :store/uuid, :key :store/uuid} {:type :join, :dispatch-key :store/profile, :key :store/profile, :query [:store.profile/description :store.profile/name :store.profile/tagline :store.profile/return-policy {:store.profile/cover [:photo/id]} {:store.profile/photo [:photo/path :photo/id]}], :children [{:type :prop, :dispatch-key :store.profile/description, :key :store.profile/description} {:type :prop, :dispatch-key :store.profile/name, :key :store.profile/name} {:type :prop, :dispatch-key :store.profile/tagline, :key :store.profile/tagline} {:type :prop, :dispatch-key :store.profile/return-policy, :key :store.profile/return-policy} {:type :join, :dispatch-key :store.profile/cover, :key :store.profile/cover, :query [:photo/id], :children [{:type :prop, :dispatch-key :photo/id, :key :photo/id}]} {:type :join, :dispatch-key :store.profile/photo, :key :store.profile/photo, :query [:photo/path :photo/id], :children [{:type :prop, :dispatch-key :photo/path, :key :photo/path} {:type :prop, :dispatch-key :photo/id, :key :photo/id}]}]} {:type :join, :dispatch-key :store/owners, :key :store/owners, :query [{:store.owner/user [:user/email]}], :children [{:type :join, :dispatch-key :store.owner/user, :key :store.owner/user, :query [:user/email], :children [{:type :prop, :dispatch-key :user/email, :key :user/email}]}]} {:type :prop, :dispatch-key :store/stripe, :key :store/stripe} {:type :join, :dispatch-key :store/sections, :key :store/sections, :query [:db/id :store.section/label], :children [{:type :prop, :dispatch-key :db/id, :key :db/id} {:type :prop, :dispatch-key :store.section/label, :key :store.section/label}]} {:type :join, :dispatch-key :order/_store, :key :order/_store, :query [:order/items], :children [{:type :prop, :dispatch-key :order/items, :key :order/items}]} {:type :join, :dispatch-key :store/items, :key :store/items, :query [:store.item/name :store.item/description :store.item/price :store.item/index {:store.item/section [:db/id :store.section/label]} {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]} :store.item.photo/index]} {:store.item/skus [:db/id :store.item.sku/inventory :store.item.sku/variation]}], :children [{:type :prop, :dispatch-key :store.item/name, :key :store.item/name} {:type :prop, :dispatch-key :store.item/description, :key :store.item/description} {:type :prop, :dispatch-key :store.item/price, :key :store.item/price} {:type :prop, :dispatch-key :store.item/index, :key :store.item/index} {:type :join, :dispatch-key :store.item/section, :key :store.item/section, :query [:db/id :store.section/label], :children [{:type :prop, :dispatch-key :db/id, :key :db/id} {:type :prop, :dispatch-key :store.section/label, :key :store.section/label}]} {:type :join, :dispatch-key :store.item/photos, :key :store.item/photos, :query [{:store.item.photo/photo [:photo/path :photo/id]} :store.item.photo/index], :children [{:type :join, :dispatch-key :store.item.photo/photo, :key :store.item.photo/photo, :query [:photo/path :photo/id], :children [{:type :prop, :dispatch-key :photo/path, :key :photo/path} {:type :prop, :dispatch-key :photo/id, :key :photo/id}]} {:type :prop, :dispatch-key :store.item.photo/index, :key :store.item.photo/index}]} {:type :join, :dispatch-key :store.item/skus, :key :store.item/skus, :query [:db/id :store.item.sku/inventory :store.item.sku/variation], :children [{:type :prop, :dispatch-key :db/id, :key :db/id} {:type :prop, :dispatch-key :store.item.sku/inventory, :key :store.item.sku/inventory} {:type :prop, :dispatch-key :store.item.sku/variation, :key :store.item.sku/variation}]}]} {:type :join, :dispatch-key :stream/_store, :key :stream/_store, :query [:stream/state], :children [{:type :prop, :dispatch-key :stream/state, :key :stream/state}]} {:type :join, :dispatch-key :store/shipping, :key :store/shipping, :query [:shipping/policy], :children [{:type :prop, :dispatch-key :shipping/policy, :key :shipping/policy}]}], :params {:store-id "17592186045446"}} {:type :join, :dispatch-key :query/store, :key :query/store, :query [{:store/status [:status/type]} {:store/profile [:store.profile/photo :store.profile/cover :store.profile/email]} {:store/shipping [:shipping/rules]} :store/username :store/items {:store/stripe [{:stripe/status [:status/type]}]}], :children [{:type :join, :dispatch-key :store/status, :key :store/status, :query [:status/type], :children [{:type :prop, :dispatch-key :status/type, :key :status/type}]} {:type :join, :dispatch-key :store/profile, :key :store/profile, :query [:store.profile/photo :store.profile/cover :store.profile/email], :children [{:type :prop, :dispatch-key :store.profile/photo, :key :store.profile/photo} {:type :prop, :dispatch-key :store.profile/cover, :key :store.profile/cover} {:type :prop, :dispatch-key :store.profile/email, :key :store.profile/email}]} {:type :join, :dispatch-key :store/shipping, :key :store/shipping, :query [:shipping/rules], :children [{:type :prop, :dispatch-key :shipping/rules, :key :shipping/rules}]} {:type :prop, :dispatch-key :store/username, :key :store/username} {:type :prop, :dispatch-key :store/items, :key :store/items} {:type :join, :dispatch-key :store/stripe, :key :store/stripe, :query [{:stripe/status [:status/type]}], :children [{:type :join, :dispatch-key :stripe/status, :key :stripe/status, :query [:status/type], :children [{:type :prop, :dispatch-key :status/type, :key :status/type}]}]}], :params {:store-id "17592186045446"}} {:type :join, :dispatch-key :query/store, :key :query/store, :query [{:store/profile [:db/id]}], :children [{:type :join, :dispatch-key :store/profile, :key :store/profile, :query [:db/id], :children [{:type :prop, :dispatch-key :db/id, :key :db/id}]}], :params {:store-id "17592186045446"}}])

(deftest test-ast-joining
  (is (not= ::fail
            (try
              (#'parser/join-ast-queries :query/store join-three-asts)
              (catch #?@(:clj  [Throwable e]
                         :cljs [:default e])
                     ::fail)))))
