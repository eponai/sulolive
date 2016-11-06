(ns eponai.web.ui.components.button
  (:require
    [om.dom :as dom]
    [taoensso.timbre :refer-macros [debug]]))

(def all-classnames
  {
   ; Base button class
   ::button "button"

   ; Should button just be outlined (default not hollow)
   ::hollow "hollow"

   ; Sizes (default medium)
   ::tiny   "tiny"
   ::small  "small"
   ::medium "medium"
   ::large  "large"

   ; Colors (default primary)
   ::primary   "primary"
   ::secondary "secondary"
   ::success   "success"
   ::alert     "alert"
   ::warning   "warning"})


(defn button [classnames]
  (let []
    ;(prn "Got values: " values)
    (conj classnames ::button)))

(defn button-style [& fns]
  (let [btn-fn (apply comp fns)]
    ((btn-fn button))))


;(defn button* []
;  (let [(-> button
;            primar)]))
;
;(defn primary []
;  (button {:styles [::primary]
;           :size   [::medium]}))
;
;(defn hollow [style]
;  (button {:styles [style ::hollow]}))



(defn success [btn-fn]
  (fn [classnames]
    (btn-fn (conj classnames ::success))))

(defn hollow [btn-fn]
  (fn [classnames]
    (btn-fn (conj classnames ::hollow))))

;(defn button*
;  (let [button-fn (-> (button)
;                      success)]
;    (prn (button-fn))))