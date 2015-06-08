(ns erinite.template-test
  (:require [clojure.test :refer :all]
            [erinite.template.core :as core]
            [erinite.template.compile :as compile]
            [erinite.template.hiccup :as hiccup]
            [erinite.template.transforms :as transforms]))

(def template-1
  [:div
    [:div#name]
    [:div.name]
    [:h1.name]
    [:a.a.b]
    [:div#x.y.z]  
    [:div.a.b]])

(def normalized-template-1
  [:div {}
    [:div {:id "name"}]
    [:div {:class "name"}]
    [:h1  {:class "name"}]
    [:a   {:class "a b"}]
    [:div {:id "x" :class "y z"}]  
    [:div {:class "a b"}]])

(def transformations-1
  {[:#name]     [:content :1]
   [:.name]     [:content :2]})

(def precompiled-transformation-1
  [{:action   :content
    :path     [2]
    :params   [:1]
    :children []}
   {:action   :content
    :path     [3]
    :params   [:2]
    :children []}
   {:action   :content
    :path     [4]
    :params   [:2]
    :children []}])

(def dummy-data-1
  {:1 "One"
   :2 "Two"})

(def rendered-template-1
  [:div {}
    [:div {:id "name"} "One"]
    [:div {:class "name"} "Two"]
    [:h1  {:class "name"} "Two"]
    [:a   {:class "a b"}]
    [:div {:id "x" :class "y z"}]  
    [:div {:class "a b"}]])



(def template-2
  [:div
    [:div#name
      [:div.first.name] 
      [:div.last.name]]
    [:ul.details
      [:li.details
        [:span "Details"]
        [:a.link {:href "http://example.com"} "link"]]]
    [:div#footer]])

(def normalized-template-2
  [:div {}
    [:div {:id "name"}
      [:div {:class "first name"}]
      [:div {:class "last name"}]]
    [:ul {:class "details"}
      [:li {:class "details"}
        [:span {} "Details"]
        [:a {:class "link" :href "http://example.com"} "link"]]]
    [:div {:id "footer"}]])

(def transformations-2
  {[:div#name :.first.name] [:content :first-name]
   [:div#name :.last.name] [:content :last-name]
   [:ul.details] [:clone-for :details]
   [:ul.details :li.details :span] [:content :details-name]})

(def precompiled-transformation-2
  [{:action   :content
    :path     [2 2]
    :params   [:first-name]
    :children []}
   {:action   :content
    :path     [2 3]
    :params   [:last-name]
    :children []}
   {:action   :clone-for
    :path     [3]
    :params   [:details]
    :children [{:action   :content
                :path     [0 2]
                :params   [:details-name]
                :children []}]}])

(def dummy-data-2
  {:first-name "First Name"
   :last-name "Last Name"
   :details [
    {:details-name "Details 1"}
    {:details-name "Details 2"}
    {:details-name "Details 3"}]})

(def rendered-template-2
  [:div {}
    [:div {:id "name"}
      [:div {:class "first name"} "First Name"]
      [:div {:class "last name"} "Last Name"]]
    [:ul {:class "details"}
      [:li {:class "details"}
        [:span {} "Details 1"]
        [:a {:class "link" :href "http://example.com"} "link"]] 
      [:li {:class "details"}
        [:span {} "Details 2"]
        [:a {:class "link" :href "http://example.com"} "link"]] 
      [:li {:class "details"}
        [:span {} "Details 3"]
        [:a {:class "link" :href "http://example.com"} "link"]]]
    [:div {:id "footer"}]])



(deftest parse-hiccup-tag-test
  (testing "id without tag"
    (is (= {:tag nil :id "x"}
           (hiccup/parse-tag :#x))))
  (testing "tag without id or class"
    (is (= {:tag :div}
           (hiccup/parse-tag :div))))
  (testing "tag with id but no class"
    (is (= {:tag :div :id "test"}
           (hiccup/parse-tag :div#test))))
  (testing "tag without id but with class"
    (is (= {:tag :div :class "test"}
           (hiccup/parse-tag :div.test))))
  (testing "tag with id and class"
    (is (= {:tag :div :id "xyz" :class "test"}
           (hiccup/parse-tag :div#xyz.test))))
  (testing "tag without id but multiple classes"
    (is (= {:tag :div :class "xyz abc"}
           (hiccup/parse-tag :div.xyz.abc))))
  (testing "tag with id and multiple classes"
    (is (= {:tag :div :id "test" :class "xyz abc"}
           (hiccup/parse-tag :div#test.xyz.abc))))
  (testing "id without tag"
    (is (= {:tag nil :id "x"}
           (hiccup/parse-tag :#x))))
  (testing "class without tag"
    (is (= {:tag nil :class "x"}
           (hiccup/parse-tag :.x))))
  (testing "id and class without tag"
    (is (= {:tag nil :id "x" :class "y"}
           (hiccup/parse-tag :#x.y)))))


(deftest normalize-hiccup-test
  (testing "normalization of hiccup, simple"
    (is (= normalized-template-1
           (hiccup/normalize template-1))))
  (testing "normalization of hiccup, more complex"
    (is (= normalized-template-2
           (hiccup/normalize template-2)))))


(deftest generate-sel-test
  (testing "generate selector set, one class and id"
    (is (= #{:div :#id :.class}
           (@#'compile/generate-sel
             :div {:id "id" :class "class"}))))
  (testing "generate selector set, multiple classes and id"
    (is (= #{:div :#id :.class-a :.class-b :.class-c}
           (@#'compile/generate-sel
             :div {:id "id" :class "class-a class-b class-c"}))))
  (testing "generate selector set, multiple classes no id"
    (is (= #{:div :.class-a :.class-b :.class-c}
           (@#'compile/generate-sel
             :div {:class "class-a class-b class-c"})))))


(deftest matches-sel-pattern-test
  (testing "matches [:div#id.class]"
    (is (@#'compile/matches-sel-pattern?
          [#{:div :.class :.foo :#id}]
          [#{:div :.class :#id}])))
  (testing "matches [:div#id :div.class :a]"
    (is (@#'compile/matches-sel-pattern?
          [#{:div :#id :.foo} #{:div :#id2 :.class} #{:a :.class2}]
          [#{:div :#id} #{:div :.class} #{:a}])))
  (testing "matches [:div#a ... :div#b]"
    (is (@#'compile/matches-sel-pattern?
          [#{:div :#a} #{:div} #{:a} #{:div :#b}]
          [#{:div :#a} #{:div :#b}]))))


(deftest precompile-transform-test
  (testing "precompile simple template transformations"
    (is (= (set precompiled-transformation-1)
           (set
             (compile/precompile-transforms
               normalized-template-1
               transformations-1)))))
  (testing "precompile more complex template transformations"
    (is (= (set precompiled-transformation-2)
           (set
             (compile/precompile-transforms
               normalized-template-2
               transformations-2))))))


(deftest render-test
  (testing "render simple template"
    (let [render  (compile/compile
                    normalized-template-1
                    (compile/compile-transforms
                      precompiled-transformation-1
                      transforms/default-transforms))]
      (is (= rendered-template-1
             (render dummy-data-1)))))
  (testing "render more complex template"
    (let [render  (compile/compile
                    normalized-template-2
                    (compile/compile-transforms
                      precompiled-transformation-2
                      transforms/default-transforms))]
      (is (= rendered-template-2
             (render dummy-data-2))))))


(deftest full-test
  (testing "render simple template"
    (let [template    (hiccup/normalize template-1)
          precompiled (compile/precompile-transforms
                        template
                        transformations-1)
          transforms  (compile/compile-transforms
                        precompiled
                        transforms/default-transforms)
          render      (compile/compile
                        template
                        transforms)]
      (is (= template normalized-template-1) "Template normalized")
      (is (= precompiled precompiled-transformation-1) "Transformations precomfiled")
      (is (= rendered-template-1
             (render dummy-data-1))
          "render")))
  (testing "render more complex template"
    (let [template    (hiccup/normalize template-2)
          precompiled (compile/precompile-transforms
                        template
                        transformations-2)
          transforms  (compile/compile-transforms
                        precompiled
                        transforms/default-transforms)
          render      (compile/compile
                        template
                        transforms)]
      (is (= template normalized-template-2) "Template normalized")
      (is (= precompiled precompiled-transformation-2) "Transformations precomfiled")
      (is (= rendered-template-2
             (render dummy-data-2))
          "render"))))


;; Test public API

(deftest compile-template-test
  (testing "render simple template"
    (let [render (core/compile-template template-1 transformations-1)]
      (is (= rendered-template-1
             (render dummy-data-1)))))
  (testing "render more complex template"
    (let [render (core/compile-template template-2 transformations-2)]
      (is (= rendered-template-2
             (render dummy-data-2))))))


(deftest preprocess-template-test
  (testing "preprocess simple template"
    (is (= {:template normalized-template-1
            :transforms precompiled-transformation-1}
           (core/preprocess-template template-1 transformations-1))))
  (testing "preprocess more complex template"
    (is (= {:template normalized-template-2
            :transforms precompiled-transformation-2}
           (core/preprocess-template template-2 transformations-2)))))


(deftest compile-preprocessed-test
  (testing "render simple template"
    (let [render  (core/compile-preprocessed
                    {:template normalized-template-1
                     :transforms precompiled-transformation-1})]
      (is (= rendered-template-1
             (render dummy-data-1)))))
  (testing "render more complex template"
    (let [render  (core/compile-preprocessed
                    {:template normalized-template-2
                     :transforms precompiled-transformation-2})]
      (is (= rendered-template-2
             (render dummy-data-2))))))


(deftest transformation-test
  (testing "compile invalid transformation"
    ;; TODO: Decide what this should bo. Currently simply ignores transformation
    ;; - should it be an error to compile with invalid/missing xforms??
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:data "test"} "dummy content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:invalid-xform]})
            {}))))

  (testing "no nop transformation"
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:data "test"} "dummy content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:nop]})
            {}))))

  (testing "content transformation"
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:data "test"} "real content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:content :real]})
            {:real "real content"}))))

  (testing "content-global transformation"
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:data "test"} "real content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:content-global :real]})
            {:real "real content"}))))

  (testing "clone-for transformation"
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:data "test"} "dummy content"
                                                "dummy content"
                                                "dummy content"
                                                "dummy content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:clone-for :seq]})
            {:seq [1 2 3 4]}))))

  (testing "set-classes transformation"
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:data "test" :class "foo baz"} "dummy content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:set-classes :classes]})
            {:classes {:foo true :bar false :baz true}}))))

  (testing "set-class transformation"
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:data "test" :class "foo"} "dummy content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:set-class :foo :should-set-foo?]})
            {:should-set-foo? true}))
        "setting class")
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:data "test" :class ""} "dummy content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:set-class :foo :should-set-foo?]})
            {:should-set-foo? false}))
        "not setting class") 
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:data "test" :class ""} "dummy content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test" :class "foo"} "dummy content"]]]
              {[:#a :.b :div] [:set-class :foo :should-set-foo?]})
            {:should-set-foo? false}))
        "removing class"))

  (testing "append-content transformation"
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:data "test"} "dummy content" "real content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:append-content :real]})
            {:real "real content"}))))

  (testing "prepend-content transformation"
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:data "test"} "real content" "dummy content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:prepend-content :real]})
            {:real "real content"}))))

  (testing "set-element-type transformation"
    (is (= [:div {:id "a"} [:div {:class "b"} [:a {:data "test"} "dummy content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:set-element-type :type]})
            {:type :a}))))

  (testing "set-attr transformation"
    (is (= [:div {:id "a"} [:div {:class "b"} [:div {:id "foo" :data "test"} "dummy content"]]]
           ((core/compile-template
              [:div#a [:div.b [:div {:data "test"} "dummy content"]]]
              {[:#a :.b :div] [:set-attr :id :attr-val]})
            {:attr-val "foo"})))))

