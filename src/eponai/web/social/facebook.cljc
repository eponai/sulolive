(ns eponai.web.social.facebook
  (:require
    [taoensso.timbre :refer [debug]]))

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