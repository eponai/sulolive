(ns eponai.common.database.rules
  #?(:cljs
     (:require-macros [eponai.common.database.rules :refer [defrule]])))

;; We define rules as 0-argument functions so we can dedupe them
;; and create them when creating our queries.
;; Use macro defrule to create less garbage by memoizing the return
;; of all rules.

#?(:clj
   (defmacro defrule [name & body]
     `(def ~name (memoize (fn [] ~@body)))))

(defrule listed-store
  '[[(listed-store ?store ?locality)
     [?store :store/locality ?locality]
     [?store :store/status ?status]
     [?status :status/type :status.type/open]
     [?store :store/profile ?profile]
     [?profile :store.profile/photo _]]])

(defrule category-or-child-category
  ;; Recursive rule. First defining a base case, i.e. if ?c is a category
  ;; Then defining the rule again where it'll traverse all children and
  ;; their children.
  '[[(category-or-child-category ?category ?return)
     [?category :category/name _]
     [(identity ?category) ?return]]
    [(category-or-child-category ?category ?recur)
     [?category :category/children ?child]
     (category-or-child-category ?child ?recur)]])

(defrule category-or-parent-category
  ;; Similar to category-or-children-category, but
  ;; recurses through parents
  '[[(category-or-parent-category ?category ?return)
     [?category :category/name _]
     [(identity ?category) ?return]]
    [(category-or-parent-category ?category ?recur)
     [?parent :category/children ?category]
     (category-or-parent-category ?parent ?recur)]])