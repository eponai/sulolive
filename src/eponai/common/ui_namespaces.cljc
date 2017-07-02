(ns eponai.common.ui-namespaces
  (:require
    [eponai.common.ui.help]
    [eponai.common.ui.checkout]
    [eponai.common.ui.shopping-bag]
    [eponai.common.ui.store]
    [eponai.common.ui.store.dashboard]
    [eponai.common.ui.goods]
    [eponai.common.ui.index]
    [eponai.common.ui.product-page]
    [eponai.common.ui.streams]
    [eponai.common.ui.user]
    [eponai.web.ui.about-sulo]
    [eponai.web.ui.landing-page]
    [eponai.web.ui.login]
    [eponai.web.ui.not-found]
    [eponai.web.ui.start-store]
    [eponai.web.ui.tos]
    [eponai.web.ui.unauthorized]
    [eponai.web.ui.user.settings]
    #?(:cljs [eponai.web.chat])))

;; WARNING
;; This namespace should not be required by any
;; .cljs namespace other than env/client/dev/env/web/main.cljs
;; Requiring this namespace in :advanced compilation breaks
;; closure module code splitting.