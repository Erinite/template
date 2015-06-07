(ns erinite.template-test
  (:require [clojure.test :refer :all]
            [erinite.template.core :as tcore]
            [erinite.template.compile :as tcompile]
            [erinite.template.hiccup :as thiccup]))

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


(def transformations-2
  {[:div#name]  [:content :1]
   [:div.name]  [:content :2]
   [:div.a.b]   [:content :3]})

(def transformations-3
  {[:div :a :span]    [:content :1]
   [:div :a.x :span]  [:content :2]})


(deftest parse-hiccup-tag-test
  (testing "id without tag"
    (is (= {:tag nil :id "x"}
           (thiccup/parse-tag :#x))))
  (testing "tag without id or class"
    (is (= {:tag :div}
           (thiccup/parse-tag :div))))
  (testing "tag with id but no class"
    (is (= {:tag :div :id "test"}
           (thiccup/parse-tag :div#test))))
  (testing "tag without id but with class"
    (is (= {:tag :div :class "test"}
           (thiccup/parse-tag :div.test))))
  (testing "tag with id and class"
    (is (= {:tag :div :id "xyz" :class "test"}
           (thiccup/parse-tag :div#xyz.test))))
  (testing "tag without id but multiple classes"
    (is (= {:tag :div :class "xyz abc"}
           (thiccup/parse-tag :div.xyz.abc))))
  (testing "tag with id and multiple classes"
    (is (= {:tag :div :id "test" :class "xyz abc"}
           (thiccup/parse-tag :div#test.xyz.abc))))
  (testing "id without tag"
    (is (= {:tag nil :id "x"}
           (thiccup/parse-tag :#x))))
  (testing "class without tag"
    (is (= {:tag nil :class "x"}
           (thiccup/parse-tag :.x))))
  (testing "id and class without tag"
    (is (= {:tag nil :id "x" :class "y"}
           (thiccup/parse-tag :#x.y)))))


(deftest normalize-hiccup-test
  (testing "normalization of hiccup, simple"
    (is (= normalized-template-1
           (thiccup/normalize template-1))))
  (testing "normalization of hiccup, more complex"
    (is (= normalized-template-2
           (thiccup/normalize template-2)))))


(deftest generate-sel-test
  (testing "generate selector set, one class and id"
    (is (= #{:div :#id :.class}
           (@#'tcompile/generate-sel
             :div {:id "id" :class "class"}))))
  (testing "generate selector set, multiple classes and id"
    (is (= #{:div :#id :.class-a :.class-b :.class-c}
           (@#'tcompile/generate-sel
             :div {:id "id" :class "class-a class-b class-c"}))))
  (testing "generate selector set, multiple classes no id"
    (is (= #{:div :.class-a :.class-b :.class-c}
           (@#'tcompile/generate-sel
             :div {:class "class-a class-b class-c"})))))


(deftest matches-sel-pattern-test
  (testing "matches [:div#id.class]"
    (is (@#'tcompile/matches-sel-pattern?
          [#{:div :.class :.foo :#id}]
          [#{:div :.class :#id}])))
  (testing "matches [:div#id :div.class :a]"
    (is (@#'tcompile/matches-sel-pattern?
          [#{:div :#id :.foo} #{:div :#id2 :.class} #{:a :.class2}]
          [#{:div :#id} #{:div :.class} #{:a}])))
  (testing "matches [:div#a ... :div#b]"
    (is (@#'tcompile/matches-sel-pattern?
          [#{:div :#a} #{:div} #{:a} #{:div :#b}]
          [#{:div :#a} #{:div :#b}]))))


(deftest precompile-transform-test
  (testing "precompile simple template transformations"
    (is (= precompiled-transformation-1
           (tcompile/precompile-transforms
             normalized-template-1
             transformations-1))))
  #_(testing "precompile more complex template transformations"
    (is (= precompiled-transformation-2
           (tcompile/precompile-transforms
             normalized-template-2
             transformations-2)))))


