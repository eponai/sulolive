(ns eponai.common.ui.product-item
  (:require
    [eponai.common.ui.product :as product]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    #?(:cljs
       [eponai.web.utils :as utils])
    [taoensso.timbre :refer [debug]]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.photo :as photo]
    [clojure.string :as string]
    [cemerick.url :as url]
    #?(:cljs
       [eponai.web.firebase :as firebase])
    [eponai.common.shared :as shared]))



