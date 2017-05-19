(ns eponai.web.social.facebook
  (:require
    [taoensso.timbre :refer [debug]]))

;FB.ui({
;       method: 'share_open_graph',
;       action_type: 'og.likes',
;       action_properties: JSON.stringify({
;                                          object:'https://developers.facebook.com/docs/',
;                                          })
;       }, function(response){});

; 834534123363063
;(defn share-stream-button []
;  (dom/a ))

(defn share-stream [stream]
  #?(:cljs
     (js/FB.ui #js {:method  "share"
                    ;:action_type       "og.likes"
                    :href    "https://sulo.live/store/17592186045420"
                    :display "page"
                    ;:action_properties (js/JSON.stringify #js{:object "834534123363063"})
                    }
               (fn [response]
                 (debug "Facebook response: " response)))))