(defproject net.tbt-post/consul-clj "0.1.2"
  :description "Clojure library for registering service with consul agent"
  :url "https://github.com/tbt-post/consul-clj"
  :license {:name "MIT"}
  :dependencies [[http-kit "2.4.0"]
                 [cheshire "5.10.0"]
                 [org.clojure/core.async "1.3.610"]
                 [org.clojure/tools.logging "1.1.0"]]
  :profiles {:uberjar {:aot :all}
             :dev     {:dependencies [[org.clojure/clojure "1.10.1"]]}}
  :plugins [[lein-ancient "0.6.15"]])
