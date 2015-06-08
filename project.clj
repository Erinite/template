(defproject erinite/template "0.2.0"
  :description "Hiccup transformation library"
  :url "https://github.com/Erinite/template"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/clojurescript "0.0-3308" :exclusions [org.clojure/clojure]]
                 [org.clojure/core.match "0.2.2"]]
  :profiles {
    :dev {
      :source-paths ["src" "test"]
      :dependencies [[criterium "0.4.3"]]}})
