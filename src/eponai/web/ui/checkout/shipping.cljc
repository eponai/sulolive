(ns eponai.web.ui.checkout.shipping
  (:require
    #?(:cljs [cljs.spec.alpha :as s]
       :clj
    [clojure.spec.alpha :as s])
    #?(:cljs
       [eponai.web.utils :as web-utils])
    #?(:cljs
       [eponai.web.google-places :as places])
    [taoensso.timbre :refer [debug]]))

(def form-inputs
  {:shipping/name             "sulo-shipping-full-name"
   :shipping.address/street   "sulo-shipping-street-address-1"
   :shipping.address/street2  "sulo-shipping-street-address-2"
   :shipping.address/postal   "sulo-shipping-postal-code"
   :shipping.address/locality "sulo-shipping-locality"
   :shipping.address/region   "sulo-shipping-region"
   :shipping.address/country  "sulo-shipping-country"})

(s/def :country/code (s/and string? #(re-matches #"\w{2}" %)))
(s/def :shipping/name (s/and string? #(not-empty %)))
(s/def :shipping.address/street (s/and string? #(not-empty %)))
(s/def :shipping.address/street2 (s/or :value string? :empty nil?))
(s/def :shipping.address/postal (s/and string? #(not-empty %)))
(s/def :shipping.address/locality (s/and string? #(not-empty %)))
(s/def :shipping.address/region (s/or :value #(string? (not-empty %)) :empty nil?))
(s/def :shipping.address/country (s/keys :req [:country/code]))

(s/def :shipping/address (s/keys :req [:shipping.address/street
                                       :shipping.address/postal
                                       :shipping.address/locality]

                                 :opt [
                                       ;:shipping.address/street2
                                       :shipping.address/region]))

(s/def ::shipping (s/keys :req [:shipping/address
                                :shipping/name]))

(def regions
  {"CA" ["ON" "QC" "NS" "NB" "MB" "BC" "PE" "SK" "AB" "NL"]
   "US" ["AL" "AK" "AZ" "AR" "CA" "CO" "CT" "DE" "FL" "GA"
         "HI" "ID" "IL" "IN" "IA" "KS" "KY" "LA" "ME" "MD"
         "MA" "MI" "MN" "MS" "MO" "MT" "NE" "NV" "NH" "NJ"
         "NM" "NY" "NC" "ND" "OH" "OK" "OR" "PA" "RI" "SC"
         "SD" "TN" "TX" "UT" "VT" "VA" "WA" "WV" "WI" "WY"]
   })

(def region-names
  {"CA" "Province"
   "US" "State"})



