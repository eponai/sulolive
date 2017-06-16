(ns eponai.common.location)

(defprotocol ILocationResponder
  (-prompt-location-picker [this]))