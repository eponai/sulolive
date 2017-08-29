(def npm-deps {:react-helmet "5.1.3"
               :react "16.0.0-beta.5"
               :react-dom "16.0.0-beta.5"
               :create-react-class "15.6.0"
               :react-grid-layout "0.15.0"})
(def closure-warns {:non-standard-jsdoc :off})

(defn modules [output-dir]
  (letfn [(path [route module?]
            (str output-dir "/" (when module? "closure-modules/") (name route) ".js"))
          (module [[route {:keys [entries depends-on output-to] :as m}]]
            [route (cond-> m
                     (nil? output-to)
                     (assoc :output-to (path route true))
                     (some? depends-on)
                     (update :depends-on set)
                     :always
                     (update :entries set))])]
    (into {}
      (map module)
      `{
        ;; Routes
        :main            {:entries [env.web.main]}
        :index           {:entries [eponai.common.ui.index]}
        :unauthorized    {:entries [eponai.web.ui.unauthorized]}
        :login           {:entries [eponai.web.ui.login-page]}
        :landing-page    {:entries [eponai.web.ui.landing-page]}
        :sell            {:entries [eponai.web.ui.start-store]}
        :store           {:entries [eponai.common.ui.store]}
        :stores          {:entries [eponai.web.ui.stores]}
        :tos             {:entries [eponai.web.ui.tos]}
        :checkout        {:entries [eponai.web.ui.checkout]}
        :browse          {:entries [eponai.common.ui.goods]}
        :shopping-bag    {:entries [eponai.common.ui.shopping-bag]}
        :product         {:entries [eponai.common.ui.product-page]}
        :live            {:entries [eponai.common.ui.streams]}
        :help            {:entries [eponai.common.ui.help]}
        :user            {:entries [eponai.common.ui.user]}
        :about           {:entries [eponai.web.ui.about-sulo]}
        :user-settings   {:entries [eponai.web.ui.user.settings]}
        :store-dashboard {:entries [eponai.common.ui.store.dashboard]}})))

(defproject budget "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojars.petterik/om "1.0.0-alpha49-SNAPSHOT-5"]
                 ;;[org.omcljs/om "1.0.0-alpha46"]
                 [io.netty/netty-all "4.1.11.Final"]
                 [aleph "0.4.3" :exclusions [io.netty/netty-all]]
                 [aleph-middleware "0.1.2" :exclusions [aleph]]
                 [clj-http "3.3.0" :exclusions [riddley]]
                 [clj-time "0.13.0"]
                 [compojure "1.5.1"]
                 [alxlit/autoclave "0.2.0"
                  :exclusions [com.google.guava/guava]]
                 [buddy/buddy-auth "1.3.0"]
                 [com.cemerick/url "0.1.1"]
                 [org.apache.logging.log4j/log4j-api "2.6.2"]
                 [org.apache.logging.log4j/log4j-to-slf4j "2.6.2"]
                 [com.datomic/datomic-pro "0.9.5544"
                  :exclusions [joda-time]]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.11.77"
                  :exclusions [joda-time org.clojure/test.check]]
                 [amazonica "0.3.85"
                  :exclusions [com.google.protobuf/protobuf-java]]
                 ;; Using our own com.taoensso/encore to exclude cljs.test and
                 ;; cljs.pprint from advanced mode compilation.
                 [org.clojars.petterik/encore "2.91.1-SNAPSHOT"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.taoensso/sente "1.11.0"]
                 [com.draines/postal "2.0.2"]
                 [environ "1.1.0"]
                 [hiccup "1.0.5"]
                 [org.clojure/data.generators "0.1.2"]
                 [org.clojure/core.async "0.3.442"]
                 [org.clojure/core.memoize "0.5.9"]
                 [org.clojure/data.json "0.2.6"]
                 [com.stuartsierra/component "0.3.2"]
                 [suspendable "0.1.1"]
                 ;; Depending on transit-clj for faster cljs builds
                 [com.cognitect/transit-clj "0.8.300"]
                 ; ring helpers
                 [org.elasticsearch.client/x-pack-transport "5.4.2"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-devel "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-ssl "0.2.1"]
                 [ring/ring-anti-forgery "1.0.1"]
                 [ring-transit "0.1.6"]
                 [ring/ring-json "0.4.0" :exclusions [cheshire]]
                 ;; For ring-json
                 [cheshire "5.7.1" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [medley "0.8.3"]
                 [org.martinklepsch/s3-beam "0.6.0-alpha1"]
                 [slingshot "0.12.2"]
                 [inflections "0.13.0"]
                 [com.google.firebase/firebase-admin "5.2.0"]

                 ;; CLJS
                 [cljsjs/react "16.0.0-beta.5-0"]
                 [cljsjs/react-dom "16.0.0-beta.5-0"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [org.clojure/clojurescript "1.9.920"
                  ;; :classifier "aot"
                  :exclusion [org.clojure/data.json]
                  ]
                 [com.google.guava/guava "21.0"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [cljs-http "0.1.39"]
                 [org.clojure/tools.reader "1.0.0-beta4"]
                 [garden "1.3.2"]
                 [datascript "0.15.5"]
                 [datascript-transit "0.2.2"
                  :exclusions [com.cognitect/transit-clj
                               com.cognitect/transit-cljs]]
                 [cljsjs/stripe "2.0-0"]
                 [cljsjs/quill "1.1.0-3"]
                 [cljsjs/react-grid-layout "0.13.7-0"]
                 [cljsjs/firebase "4.0.0-0"]
                 [bidi "2.0.10"]
                 [kibu/pushy "0.3.6"]
                 [prone "1.1.4"]

                 ;; React-native
                 [natal-shell "0.3.0"]

                 ;; Testing
                 ]


  :exclusions [org.clojure/test.check
               org.clojure/clojure
               org.clojure/clojurescript
               org.clojure/core.memoize
               cljsjs/react
               ;; Using our own version which doesn't refer to cljs.test and cljs.pprint
               com.taoensso/encore]

  :jvm-opts ^:replace ["-Xms512m"
                       "-Xmx3048m"
                       "-server"
                       "-XX:+CMSClassUnloadingEnabled"
                       "-Djava.awt.headless=true"
                       ]
  :resource-paths ["resources" "node_modules" "bower_components"]

  :plugins [
            ;; [lein-npm "0.6.1"]
            [lein-shell "0.5.0"]
            [lein-doo "0.1.7" :exclusions [org.clojure/clojure org.clojure/clojurescript]]
            [lein-cljsbuild "1.1.7" :exclusions [org.clojure/clojure org.clojure/clojurescript]]
            [lein-figwheel "0.5.13" :exclusions [org.clojure/clojure]]
            [lein-environ "1.1.0"]]

  :min-lein-version "2.0.0"
  :clean-targets ^{:protect false} ["resources/public/dev/js/out"
                                    "resources/public/devcards/js/out"
                                    ;; Don't clean release, as we don't want "lein clean" to
                                    ;; remove our build javascript from the release dir.
                                    ;; See circle.yml (CircleCi)
                                    ;; "resources/public/release/js/out"
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
            "css"                    ["shell" "./scripts/compile-css.sh"]
            "css-watch"              ["shell" "./scripts/compile-css.sh" "watch"]
            "prod-build-web"         ^{:doc "Recompile web code with release build."}
                                     ["do"
                                      ["with-profile" "+web-prod" "cljsbuild" "once" "release"]]
            "prod-build-server"      ^{:doc "Recompile server code with release build."}
                                     ["do" "uberjar"]
            "simple-build-web"       ^{:doc "Recompile web code with release build."}
                                     ["do"
                                      ["with-profile" "+web" "cljsbuild" "once" "simple"]]
            "simple-build-web-auto"  ^{:doc "Recompile web code with release build."}
                                     ["do"
                                      ["with-profile" "+web" "cljsbuild" "auto" "simple"]]
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
             :css-dirs        ["resources/public/assets/css/"]
             :server-port     ~(read-string (or (System/getenv "FIGWHEEL_PORT") "3449"))
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
                                       [figwheel-sidecar "0.5.13"]
                                       [com.cemerick/piggieback "0.2.1"]
                                       ]
                        :repl-options {:timeout 120000
                                       :init-ns eponai.repl
                                       :init    (eponai.repl/init)}
                        :test-paths   ["test" "env/server/dev"]}
             :tester   {:dependencies [[lein-cljsbuild "1.1.7"]
                                       [cljsbuild "1.1.7"]
                                       [clj-stacktrace "0.2.5"]
                                       [lein-doo "0.1.7"
                                        :exclusions [org.clojure/clojure]]]}
             :uberjar  {:jvm-opts       ^:replace ["-Dclojure.compiler.direct-linking=true"
                                                   "-Xmx1g" "-server"]
                        :aot            :all
                        :resource-paths ^:replace ["resources"]}

             :web-prod {:dependencies [[amazonica "0.3.85"
                                        :exclusions [com.taoensso/encore
                                                     com.google.protobuf/protobuf-java]]
                                       ;; [binaryage/devtools "0.9.4"]
                                       ]
                        :jvm-opts     ^:replace ["-Xmx3g" "-server"]
                        :cljsbuild    {:builds [{:id           "release"
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
                                                                :infer-externs   true
                                                                ;; :preloads [env.web.preloads]
                                                                :language-in     :ecmascript5
                                                                :parallel-build  true
                                                                :pseudo-names true
                                                                :pretty-print true
                                                                :elide-asserts   true
                                                                :verbose         true
                                                                :compiler-stats  true
                                                                :npm-deps        ~npm-deps
                                                                ;;:install-deps    true
                                                                :modules         ~(modules "resources/public/release/js/out/")
                                                                }}]}}
             :web      {:jvm-opts  ^:replace ["-Xmx3g" "-server"]
                        :cljsbuild {:builds [{:id           "dev"
                                              :figwheel     {:on-jsload "eponai.web.app/reload!"}
                                              :source-paths ["src/" "src-hacks/web/" "env/client/dev"]
                                              :compiler     {;; :main                 "env.web.main"
                                                             :asset-path           "/dev/js/out"
                                                             :output-to            "resources/public/dev/js/out/figwheel.js"
                                                             ;; :modules ~(modules "/dev/js/out")
                                                             :output-dir           "resources/public/dev/js/out/"
                                                             :optimizations        :none
                                                             :parallel-build       true
                                                             :source-map           true
                                                             :language-in          :ecmascript5
                                                             :npm-deps             ~npm-deps
                                                             :verbose              true
                                                             :install-deps         true
                                                             :closure-warnings     ~closure-warns
                                                             ;; Speeds up Figwheel cycle, at the risk of dependent namespaces getting out of sync.
                                                             :recompile-dependents false
                                                             :modules              ~(modules "resources/public/dev/js/out/")
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
                                                             :closure-warnings     ~closure-warns
                                                             :npm-deps             ~npm-deps
                                                             :recompile-dependents false}}
                                             {:id           "test"
                                              :source-paths ["src/" "src-hacks/web/" "test/"]
                                              :figwheel     {:on-jsload "eponai.client.figwheel.test-main/reload-figwheel!"}
                                              :compiler     {:output-to            "resources/public/test/js/out/budget.js"
                                                             :output-dir           "resources/public/test/js/out"
                                                             :asset-path           "/test/js/out"
                                                             :main                 "eponai.client.figwheel.test-main"
                                                             :parallel-build       true
                                                             :optimizations        :none
                                                             :source-map           true
                                                             :closure-warnings     ~closure-warns
                                                             :npm-deps             ~npm-deps
                                                             :recompile-dependents false
                                                             }}
                                             {:id           "doo-test"
                                              :source-paths ["src/" "src-hacks/web/" "test/"]
                                              :compiler     {:output-to        "resources/public/doo-test/js/out/budget.js"
                                                             :output-dir       "resources/public/doo-test/js/out"
                                                             :main             "eponai.client.tests"
                                                             :parallel-build   false
                                                             :optimizations    :none
                                                             :source-map       true
                                                             :closure-warnings ~closure-warns
                                                             :npm-deps         ~npm-deps
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
                                                             :infer-externs   true
                                                             ;; :language-in     :ecmascript5
                                                             :parallel-build  true
                                                             :pretty-print    true
                                                             :compiler-stats  true
                                                             :verbose         true
                                                             ;; :source-map   true
                                                             :npm-deps        ~npm-deps
                                                             :modules         ~(modules "resources/public/simple/js/out/")
                                                             }}]}}}

  ;;;;;;;;;;;;;
  ;; clj:
  ;;;;;;;;;;;;;
  :target-path "target/%s"
  :source-paths ["src"]
  :ring {:handler eponai.server.core/app
         :init    eponai.server.core/init}
  :main eponai.server.core
  :repositories {"elasticsearch"  {:url      "https://artifacts.elastic.co/maven"
                                   :releases true
                                   :snapshots false}
                 "my.datomic.com" {:url      "https://my.datomic.com/repo"
                                   :username ~(System/getenv "DATOMIC_EMAIL")
                                   :password ~(System/getenv "DATOMIC_KEY")}}

  ;;;;;;;;;;;;;
  ;; cljs:
  ;;;;;;;;;;;;;
  :doo {:paths {:phantom "./node_modules/phantomjs/bin/phantomjs"}}
  )
