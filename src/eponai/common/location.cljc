(ns eponai.common.location)

(def locality-cookie-name "sulo.locality")

(defprotocol ILocationResponder
  (-prompt-location-picker [this]))