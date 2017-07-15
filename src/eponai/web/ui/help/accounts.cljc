(ns eponai.web.ui.help.accounts
  (:require
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.callout :as callout]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.dom :as dom]
    [eponai.client.routes :as routes]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.elements.css :as css]))

(defui Accounts
  Object
  (initLocalState [_]
    {:active/sign-in false})
  (render [this]
    (let [{:active/keys [sign-in-social sign-in-email change-profile password]} (om/get-state this)]
      (dom/div
        nil
        (callout/callout
          (css/add-class :help-menu)
          (grid/row-column
            nil
            (menu/breadcrumbs
              nil
              (menu/item nil (dom/a {:href (routes/url :help)}
                                    "SULO Live support"))
              (menu/item nil (dom/a {:href (routes/url :help/accounts)}
                                    (dom/span nil "Accounts"))))))
        (grid/row-column
          nil
          (dom/h1 nil (dom/span nil "Accounts") (dom/br nil) (dom/small nil "Managing your SULO Live account"))
          (callout/callout
            nil
            (dom/h2 nil "Basics")
            ;(dom/div
            ;  (css/add-class :navigation)
            ;  (menu/vertical
            ;    nil
            ;    ))
            (dom/div
              (css/add-class :help-items)
              (dom/section
                (when sign-in-email (css/add-class :is-active))
                (dom/a
                  (->> {:onClick #(om/update-state! this update :active/sign-in-email not)}
                       (css/add-class :header))
                  (dom/h3 nil "Sign in with email"))
                (dom/div
                  (css/add-class :help-item-content)
                  (dom/p nil "To sign into SULO Live with your email:")
                  (dom/ul nil
                          (dom/li nil "Click Sign up / Sign in, or visit sulo.live/login")
                          (dom/li nil (dom/span nil "Choose ") (dom/em nil "Sign up or sign in with email"))
                          (dom/li nil (dom/span nil "Enter your email address and click ") (dom/em nil "Email me a code to sign in"))
                          (dom/li nil (dom/span nil "Go to your email inbox and open the verification email."))
                          (dom/li nil (dom/span nil "Copy the code in the email and paste in SULO Live.")))
                  (dom/p nil "If an account with the same email already exists on SULO Live, you will be signed in to the existing account. Otherwise, you will be prompted to create a new account.")
                  ))

              (dom/section
                (when sign-in-social (css/add-class :is-active))
                (dom/a
                  (->> {:onClick #(om/update-state! this update :active/sign-in-social not)}
                       (css/add-class :header))
                  (dom/h3 nil "Sign in with Facebook/Twitter"))
                (dom/div
                  (css/add-class :help-item-content)
                  (dom/p nil "To sign into SULO Live with your Facebook or Twitter account:")
                  (dom/ul nil
                          (dom/li nil "Click Sign up / Sign in, or visit sulo.live/login")
                          (dom/li nil (dom/span nil "Choose ") (dom/em nil "Continue with Facebook") (dom/span nil " or ")
                                  (dom/em nil "Continue with Twitter"))
                          (dom/li nil "Sign in to your Facebook or Twitter account"))
                  (dom/p nil "If an account with the same email as your social account already exists on SULO Live, you will be signed in to the existing account. If no email is associated with your social account or if the associated email does not exist in our system, you will be prompted to create a new account.")))

              (dom/section
                (when change-profile (css/add-class :is-active))
                (dom/a
                  (->> {:onClick #(om/update-state! this update :active/change-profile not)}
                       (css/add-class :header))
                  (dom/h3 nil "Change username and photo"))
                (dom/div
                  (css/add-class :help-item-content)
                  (dom/p nil "Your username and photo are used in the chat to let other users regonise you. You can update your username and password in your user settings:")
                  (dom/ul nil
                          (dom/li nil "Click your profile picture in the top bar")
                          (dom/li nil "Go to Settings > Edit profile")
                          (dom/li nil "Update your username/photo and click Save"))))

              (dom/section
                (when password (css/add-class :is-active))
                (dom/a
                  (->> {:onClick #(om/update-state! this update :active/password not)}
                       (css/add-class :header))
                  (dom/h3 nil "What's my password?"))
                (dom/div
                  (css/add-class :help-item-content)
                  (dom/p nil "If you sign in with your email, you will receive a one-time verification code to sign in. If you sign in with your social media account, you will use the credentials for that account.")
                  (dom/p nil "For security reasons, SULO Live does not and will not support sign in with username/password on the site."))))))))))

(def ->Accounts (om/factory Accounts))