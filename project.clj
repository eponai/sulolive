(def npm-deps {
               ;;:dompurify "0.8.6"
              })
(def closure-warns {:non-standard-jsdoc :off})

(defn modules [output-dir]
  (letfn [(path [route module?]
            (str output-dir "/" (when module? "closure-modules/") (name route) ".js"))
          (module [[route {:keys [entries depends-on] :as m}]]
            [route (cond-> (assoc m :output-to (path route true))
                     (some? depends-on)
                     (update :depends-on set)
                     :always
                     (update :entries set))])]
    (into `{:cljs-base {:entries   #{env.web.main}
                        :output-to ~(path :budget false)}}
      (map module)
      `{
        ;; Extra groupings
        :react-select    {:entries [eponai.common.ui.components.select
                                    cljsjs.react-select]}
        :stream+chat     {:entries [eponai.common.ui.stream
                                    eponai.common.stream
                                    eponai.common.ui.chat]}
        :photo-uploader  {:entries [eponai.client.ui.photo-uploader]}

        ;; Routes
        :index           {:entries [eponai.common.ui.index]}
        :unauthorized    {:entries [eponai.web.ui.unauthorized]}
        :login           {:entries    [eponai.web.ui.login]
                          :depends-on [:index]}
        :coming-soon     {:entries [eponai.web.ui.coming-soon]}
        :sell            {:entries [eponai.web.ui.start-store]}
        :store           {:entries    [eponai.common.ui.store]
                          :depends-on [:stream+chat]}
        :checkout        {:entries [eponai.common.ui.checkout
                                    eponai.common.ui.checkout.shipping
                                    eponai.common.ui.checkout.payment
                                    eponai.common.ui.checkout.review
                                    eponai.common.ui.checkout.google-places]}
        :browse          {:entries [eponai.common.ui.goods
                                    eponai.common.ui.product-filters]}
        :shopping-bag    {:entries [eponai.common.ui.shopping-bag]}
        :product         {:entries [eponai.common.ui.product-page]}
        :live            {:entries [eponai.common.ui.streams]}
        :help            {:entries [eponai.common.ui.help
                                    eponai.common.ui.help.faq
                                    eponai.common.ui.help.first-stream
                                    eponai.common.ui.help.mobile-stream
                                    eponai.common.ui.help.quality]}
        :user            {:entries [eponai.common.ui.user
                                    eponai.common.ui.user.order-list
                                    eponai.common.ui.user.order-receipt
                                    eponai.common.ui.user.profile
                                    eponai.common.ui.user.profile-edit]
                          :depends-on [:photo-uploader]}
        :store-dashboard {:depends-on [:react-select
                                       :stream+chat
                                       :photo-uploader]
                          :entries    [eponai.common.ui.store.dashboard
                                       eponai.common.ui.store.account
                                       eponai.common.ui.store.order-edit-form
                                       eponai.common.ui.store.order-list
                                       eponai.common.ui.store.product-edit-form
                                       eponai.common.ui.store.product-list
                                       eponai.common.ui.store.stream-settings
                                       eponai.common.ui.store.account.activate
                                       eponai.common.ui.store.account.business
                                       eponai.common.ui.store.account.general
                                       eponai.common.ui.store.account.payments
                                       eponai.common.ui.store.account.payouts
                                       eponai.common.ui.store.account.shipping
                                       eponai.common.ui.store.account.validate
                                       eponai.web.ui.store.common
                                       eponai.web.ui.store.edit-store
                                       cljsjs.react-grid-layout]}})))

(defproject budget "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojars.petterik/om "1.0.0-alpha49-SNAPSHOT-1"]
                 ;;[org.omcljs/om "1.0.0-alpha46"]
                 [clj-http "3.3.0"]
                 [clj-time "0.11.0"]
                 [compojure "1.5.1"]
                 [alxlit/autoclave "0.2.0"
                  :exclusions [com.google.guava/guava]]
                 [buddy/buddy-auth "1.3.0"]
                 [com.cemerick/url "0.1.1"]
                 [com.datomic/datomic-pro "0.9.5544"
                  :exclusions [joda-time]]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.11.77"
                  :exclusions [joda-time org.clojure/test.check]]
                 [amazonica "0.3.85"]
                 [com.draines/postal "2.0.1"]
                 [com.stripe/stripe-java "3.11.0"]
                 [com.taoensso/timbre "4.8.0"]
                 [environ "1.1.0"]
                 [hiccup "1.0.5"]
                 [org.clojure/data.generators "0.1.2"]
                 [org.clojure/core.async "0.3.442"]
                 [org.clojure/core.memoize "0.5.8"]         ; needed to work around lein+core.async dependency issue.
                 [org.clojure/data.json "0.2.6"]
                 [com.stuartsierra/component "0.3.2"]
		 [suspendable "0.1.1"]
                 ;; Depending on transit-clj for faster cljs builds
                 [com.cognitect/transit-clj "0.8.300"]
                 ; ring helpers
                 [aleph "0.4.3"]
                 [aleph-middleware "0.1.2" :exclusions [aleph]]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-devel "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-ssl "0.2.1"]
                 [ring/ring-anti-forgery "1.0.1"]
                 [ring-transit "0.1.6"]
                 [ring/ring-json "0.4.0" :exclusions [cheshire]]
                 [cheshire "5.6.3"]  ;; For ring-json
                 [medley "0.8.3"]
                 [org.martinklepsch/s3-beam "0.6.0-alpha1"]
                 [com.taoensso/sente "1.11.0"]

                 ;; CLJS
                 [cljsjs/react "15.4.2-2"]
                 [cljsjs/react-dom "15.4.2-2"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [org.clojure/clojurescript "1.9.521"
                  ;;  :classifier "aot"
                  :exclusion [org.clojure/data.json]
                  ]
                 [com.google.guava/guava "21.0"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [cljs-http "0.1.39"]
                 [org.clojure/tools.reader "1.0.0-beta4"]
                 [garden "1.3.2"]
                 [datascript "0.15.4"]
                 [cljsjs/stripe "2.0-0"]
                 [cljsjs/quill "1.1.0-3"]
                 [cljsjs/react-select "1.0.0-rc.1"]
                 [cljsjs/react-grid-layout "0.13.7-0"]
                 [bidi "2.0.10"]
                 [kibu/pushy "0.3.6"]
		 [prone "1.1.4"]

                 ;; React-native
                 [natal-shell "0.3.0"]

                 ;; Testing
                 ]


  :exclusions [org.clojure/test.check
               org.clojure/clojure
               org.clojure/clojurescript]

  :jvm-opts ^:replace ["-Xms512m" "-Xmx2048m" "-server"
                       "-XX:+TieredCompilation" "-XX:TieredStopAtLevel=1"
                      ]
  :resource-paths ["resources" "node_modules" "bower_components"]

  :plugins [
           ;; [lein-npm "0.6.1"]
            [lein-shell "0.5.0"]
            [lein-doo "0.1.7" :exclusions [org.clojure/clojure org.clojure/clojurescript]]
            [lein-cljsbuild "1.1.5" :exclusions [org.clojure/clojure org.clojure/clojurescript]]
            [lein-figwheel "0.5.7" :exclusions [org.clojure/clojure]]
            [lein-test-out "0.3.1"]
            [lein-environ "1.1.0"]]

  :min-lein-version "2.0.0"
  :clean-targets ^{:protect false} ["resources/public/dev/js/out"
                                    "resources/public/devcards/js/out"
                                    "resources/public/release/js/out"
                                    "resources/public/doo-test/js/out"
                                    "resources/public/test/js/out"
                                    "target/"
                                  ;;  "index.ios.js"
                                  ;;  "index.android.js"
                                   ]

  :aliases {"all-deps"               ^{:doc "Fetches both clj, cljs and node dependencies."}
                                     ["do"
                                      ["deps"]
                                      ;;   ["pod-deps"]
                                      ["npm-deps"]
                                      ["css"]]
            "npm-deps"               ["do"
                                      ["shell" "npm" "install"]
                                      ["shell" "bower" "install"]]
            "pod-deps"               ["shell" "pod" "install" "--project-directory=./ios"]
            "css"                    ["shell" "./scripts/compile-css.sh"]
            "css-watch"              ["shell" "./scripts/compile-css.sh" "watch"]
            "prod-build-ios-local"   ^{:doc "Recompile mobile code with production profile.
                                           The build runs against a local/laptop server."}
                                     ["do"
                                      ["with-profile" "mob-prod" "cljsbuild" "once" "ios-local"]]
            "prod-build-ios-release" ^{:doc "Recompile mobile code with production profile.
                                           The build runs against production servers."}
                                     ["do"
                                      ["with-profile" "mob-prod" "cljsbuild" "once" "ios-release"]]
            "prod-build-web"         ^{:doc "Recompile web code with release build."}
                                     ["do"
                                      ["with-profile" "web-prod" "cljsbuild" "once" "release"]]
            "prod-build-server"      ^{:doc "Recompile server code with release build."}
                                     ["do" "uberjar"]
            "simple-build-web"       ^{:doc "Recompile web code with release build."}
                                     ["do"
                                      ["with-profile" "+web" "cljsbuild" "once" "simple"]]
            "simple-build-web-auto"  ^{:doc "Recompile web code with release build."}
                                     ["do"
                                      ["with-profile" "+web" "cljsbuild" "auto" "simple"]]
            "dev-build-ios"          ^{:doc "Compile mobile code in development mode."}
                                     ["do"
                                      ["with-profile" "+mobile" "cljsbuild" "once" "ios"]]
            "dev-build-web"          ^{:doc "Compile web code in development mode."}
                                     ["do"
                                      ["with-profile" "+web" "cljsbuild" "once" "dev"]]
            "dev-build-web-auto"     ^{:doc "Compile web code in development mode continously."}
                                     ["do"
                                      ["with-profile" "+web" "cljsbuild" "auto" "dev"]]
            "run-tests-web"          ^{:doc "Compile and run web tests"}
                                     ["do"
                                      ["with-profile" "+web" "cljsbuild" "once" "doo-test"]
                                      ;;["with-profile" "+web" "doo" "phantom" "doo-test" "once"]
                                      ]
            "figwheel-ios"           ^{:doc "Start figwheel for ios"}
                                     ["do"
                                      ;; Exporting an environment variable for figwheel port
                                      ;; as it's only configurable globally (per project.clj file).
                                      ;; This port should differ from the one running figwheel-web,
                                      ;; because they need to be running lein with different profiles
                                      ;; and different dependencies.
                                      ["shell" "bash" "-c"
                                       "export FIGWHEEL_PORT=3450; lein with-profile +mobile figwheel ios"]
                                      ["with-profile" "+mobile" "figwheel" "ios"]]
            "figwheel-web"           ^{:doc "Start figwheel for web"}
                                     ["do"
                                      ["with-profile" "+web" "figwheel" "dev"]
                                      ]
            "figwheel-web+devcards"  ^{:doc "Start figwheel for web"}
                                     ["do"
                                      ["with-profile" "+web" "figwheel" "dev" "devcards"]
                                      ]
            "figwheel-test"          ^{:doc "Start figwheel for web"}
                                     ["do"
                                      ["with-profile" "+web" "figwheel" "test"]]
            "figwheel-web+test"      ^{:doc "Start figwheel for web"}
                                     ["do"
                                      ["with-profile" "+web" "figwheel" "dev" "test"]]
            }

  ;; TODO: TEST ALL ALIASES

  ;;;;;;;;;;;;;
  ;; AWS+Docker deploy:
  ;;;;;;;;;;;;;
  :uberjar-name "budget-0.1.0-SNAPSHOT-standalone.jar"

  :figwheel {:validate-config :ignore-unknown-keys
             :css-dirs    ["resources/public/assets/css/"]
             :server-port ~(read-string (or (System/getenv "FIGWHEEL_PORT") "3449"))
      ;;       :reload-clj-files {:clj true :cljc true}
 }

  :profiles {:dev      {:dependencies [[lein-doo "0.1.7"
                                        :exclusions [org.clojure/clojure]]
                                       [devcards "0.2.2"]
                                       [plomber "0.1.0"]
                                       [reloaded.repl "0.2.3"
                                        :exclusions [com.stuartsierra/component]]
                                       [org.clojure/test.check "0.9.0"]
                                       [binaryage/devtools "0.9.4"]
                                       [org.clojure/tools.nrepl "0.2.11"
                                        :exclusions [org.clojure/clojure]]
                                       [vvvvalvalval/datomock "0.2.0"]
                                       [org.clojure/tools.namespace "0.2.11"]
                                       [aprint "0.1.3"]
                                       [cljsjs/nvd3 "1.8.2-1"]
                                       ]
                        :repl-options {:init-ns eponai.repl
                                       :init    (eponai.repl/init)}
                        :test-paths   ["test" "env/server/dev"]}
             :tester   {:dependencies [[lein-cljsbuild "1.1.5"]
                                       [lein-doo "0.1.7"
                                        :exclusions [org.clojure/clojure]]]}
             :uberjar  {:jvm-opts       ^:replace ["-Dclojure.compiler.direct-linking=true"
                                                   "-Xmx1g" "-server"]
                        :aot            :all
                        :resource-paths ^:replace ["resources"]
                        :prep-tasks     ["compile" "prod-build-web" "css"]}

             :mobile   {:dependencies
                                      [[org.clojars.petterik/om "1.0.0-alpha49-SNAPSHOT-1"
                                        :exclusions [cljsjs/react cljsjs/react-dom]]
                                       ;; [[org.omcljs/om "1.0.0-alpha46"
                                       ;;   :exclusions [cljsjs/react cljsjs/react-dom]]
                                       [figwheel-sidecar "0.5.7"]
                                       [com.cemerick/piggieback "0.2.1"]]
                        :source-paths ["src" "src-hacks/react-native" "env/client/dev"]
                        :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                        :cljsbuild    {:builds [{:id           "ios"
                                                 :source-paths ["src" "src-hacks/react-native" "env/client/dev"]
                                                 :figwheel     true
                                                 :compiler     {:output-to     "target/ios/not-used.js"
                                                                :main          "env.ios.main"
                                                                :output-dir    "target/ios"
                                                                :optimizations :none}}
                                                {:id           "android"
                                                 :source-paths ["src" "src-hacks/react-native" "env/client/dev"]
                                                 :figwheel     true
                                                 :compiler     {:output-to     "target/android/not-used.js"
                                                                :main          "env.android.main"
                                                                :output-dir    "target/android"
                                                                :optimizations :none}}]}}

             :mob-prod {:dependencies
                                   [[org.clojars.petterik/om "1.0.0-alpha49-SNAPSHOT-1"
                                     :exclusions [cljsjs/react cljsjs/react-dom]]]
                        ;; [[org.omcljs/om "1.0.0-alpha46"
                        ;;   :exclusions [cljsjs/react cljsjs/react-dom]]]
                        :cljsbuild {:builds [{:id           "ios-release"
                                              :source-paths ["src" "src-hacks/react-native" "env/client/prod"]
                                              :compiler     {:output-to     "index.ios.js"
                                                             :main          "env.ios.main"
                                                             :output-dir    "target/ios"
                                                             :optimizations :simple}}
                                             {:id           "ios-local"
                                              ;; A production build, run against a local/laptop server.
                                              :source-paths ["src" "src-hacks/react-native" "env/client/prod"]
                                              :compiler     {:output-to     "index.ios.js"
                                                             :main          "env.ios.local-main"
                                                             :output-dir    "target/ios-local"
                                                             :optimizations :simple}}
                                             {:id           "android"
                                              :source-paths ["src" "src-hacks/react-native" "env/client/prod"]
                                              :compiler     {:output-to     "index.android.js"
                                                             :main          "env.android.main"
                                                             :output-dir    "target/android"
                                                             :optimizations :simple}}]}}

             :web-prod {:jvm-opts     ^:replace ["-Xmx3g" "-server"]
                        :cljsbuild {:builds [{:id           "release"
                                              :source-paths ["src/" "src-hacks/web/" "env/client/prod"]
                                              :compiler     {:closure-defines {"goog.DEBUG" false}
                                                             :main            "env.web.main"
                                                             :asset-path      "/release/js/out"
                                                             :output-to       "resources/public/release/js/out/budget.js"
                                                             :output-dir      "resources/public/release/js/out/"
                                                             :optimizations   :advanced
                                                             :externs         ["src-hacks/js/externs/stripe-checkout.js"
                                                                               "src-hacks/js/externs/red5pro.js"
                                                                               "src-hacks/js/externs/hls.js"]
                                                             :infer-externs true
                                                             ;; :preloads [env.web.preloads]
                                                             ;; :language-in     :ecmascript5
                                                             ;; :parallel-build  true
                                                             ;; :pseudo-names true
                                                             ;; :pretty-print true
                                                             :verbose         true
                                                             :npm-deps        ~npm-deps
                                                             :modules ~(modules "resources/public/release/js/out/")
                                                             }}]}}
             :web      {:jvm-opts     ^:replace ["-Xmx3g" "-server"]
                        :exclusions   [org.clojure/clojure org.clojure/clojurescript]
                        :dependencies [[figwheel-sidecar "0.5.7"]]
                        :cljsbuild    {:builds [{:id           "dev"
                                                 :figwheel     {:on-jsload "eponai.web.figwheel/reload!"}
                                                 :source-paths ["src/" "src-hacks/web/" "env/client/dev"]
                                                 :compiler     {:main           "env.web.main"
                                                                :asset-path     "/dev/js/out"
                                                                :output-to      "resources/public/dev/js/out/budget.js"
                                                                ;; :modules ~(modules "/dev/js/out")
                                                                :output-dir     "resources/public/dev/js/out/"
                                                                :optimizations  :none
                                                                :parallel-build true
                                                                :source-map     true
                                                                :npm-deps       ~npm-deps
                                                                :closure-warnings ~closure-warns
                                                                }}
                                                {:id           "devcards"
                                                 :source-paths ["src/" "src-hacks/web/" "test/"]
                                                 :figwheel     {:devcards true ;; <- note this
                                                                }
                                                 :compiler     {:main                 "eponai.devcards.devcards_main"
                                                                :asset-path           "/devcards/js/out"
                                                                :output-to            "resources/public/devcards/js/out/budget.js"
                                                                :output-dir           "resources/public/devcards/js/out"
                                                                :source-map-timestamp true
                                                                :closure-warnings ~closure-warns
                                                                :npm-deps             ~npm-deps}}
                                                {:id           "test"
                                                 :source-paths ["src/" "src-hacks/web/" "test/"]
                                                 :figwheel     {:on-jsload "eponai.client.figwheel.test-main/reload-figwheel!"}
                                                 :compiler     {:output-to      "resources/public/test/js/out/budget.js"
                                                                :output-dir     "resources/public/test/js/out"
                                                                :asset-path     "/test/js/out"
                                                                :main           "eponai.client.figwheel.test-main"
                                                                :parallel-build true
                                                                :optimizations  :none
                                                                :source-map     true
                                                                :closure-warnings ~closure-warns
                                                                :npm-deps       ~npm-deps
                                                                }}
                                                {:id           "doo-test"
                                                 :source-paths ["src/" "src-hacks/web/" "test/"]
                                                 :compiler     {:output-to      "resources/public/doo-test/js/out/budget.js"
                                                                :output-dir     "resources/public/doo-test/js/out"
                                                                :main           "eponai.client.tests"
                                                                :parallel-build true
                                                                :optimizations  :none
                                                                :source-map     true
                                                                :closure-warnings ~closure-warns
                                                                :npm-deps       ~npm-deps
                                                                }}
                                                {:id           "simple"
                                                 :source-paths ["src/" "src-hacks/web/" "env/client/simple"]
                                                 :compiler     {:closure-defines {"goog.DEBUG" true}
                                                                :main            "env.web.main"
                                                                :asset-path      "/simple/js/out"
                                                                :output-to       "resources/public/simple/js/out/budget.js"
                                                                :output-dir      "resources/public/simple/js/out/"
                                                                :optimizations   :simple
                                                                :externs         ["src-hacks/js/externs/stripe-checkout.js"]
                                                                :infer-externs true
                                                                ;; :language-in     :ecmascript5
                                                                :parallel-build  true
                                                                :pretty-print true
                                                                ;; :source-map   true
                                                                ;; :verbose         true
                                                                :npm-deps        ~npm-deps
                                                                :modules ~(modules "resources/public/simple/js/out/")
                                                                }}]}}}

   ;;;;;;;;;;;;;
   ;; clj:
   ;;;;;;;;;;;;;
   :target-path  "target/%s"
   :source-paths ["src"]
   :ring         {:handler eponai.server.core/app
                  :init    eponai.server.core/init}
   :main         eponai.server.core
   :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                    :username ~(System/getenv "DATOMIC_EMAIL")
                                    :password ~(System/getenv "DATOMIC_KEY")}}

   ;;;;;;;;;;;;;
   ;; cljs:
   ;;;;;;;;;;;;;
   :doo          {:paths {:phantom "./node_modules/phantomjs/bin/phantomjs"}}
  )
