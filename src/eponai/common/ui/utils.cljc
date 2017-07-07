(ns eponai.common.ui.utils
  (:require [eponai.common :as com]))

(defn two-decimal-price [price]
  (com/format-str "$%.2f" (double (or price 0))))
