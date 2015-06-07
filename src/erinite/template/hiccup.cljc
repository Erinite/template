(ns erinite.template.hiccup
  #?(:cljs
      (:require-macros
        [cljs.core.match.macros :refer [match]]))
  (:require
    #?(:cljs [cljs.core.match]
       :clj  [clojure.core.match :refer [match]])
    [clojure.walk :as walk]))

;; From hiccup.compiler:
(def ^{:doc "Regular expression that parses a CSS-style id and class from an element name."
       :private true}
     re-tag #"([^\s\.#]*)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")


(defn parse-tag
  "Parse a hiccup keyword tag in one of the following forms:
   INPUT                        OUTPUT
    :a                          {:tag :a}
    :a#id                       {:tag :a, :id \"id\"}
    :a.class1                   {:tag :a, :class \"class1\"}
    :a.class1.class2.classN     {:tag :a, :class \"class1 class2 classN\"}
    :a#id.class1                {:tag :a, :id \"id\", :class \"class1\"}
    :a#id.class1.class2.classN  {:tag :a, :id \"id\", :class \"class1 class2 classN\"}"
  [tag]
  (let [[_ tag-name id classes] (re-matches re-tag (name tag))
        tag {:tag (when-not (empty? tag-name) (keyword tag-name))}
        tag (if id (assoc tag :id id) tag)
        tag (if classes
              (assoc tag
                     :class
                     #?(:cljs (.replace classes (js/RegExp. "\\." "g") " ")
                        :clj  (clojure.string/replace classes #"\." " ")))
              tag)]
    tag))


(defn normalize
  "Converts a hiccup template to normalized form.
   The normalized for is to convert every element into a vector:
    [element-type-kw element-attributes-map children...]
    
    element-attributes-map contains :id as a string and :class as a string of
   space separated class names. "
  [template]
  (match [template]
    [[(elem :guard keyword?) (attrs :guard map?) & args]]
          (let [{tag-name :tag :as new-attrs} (parse-tag elem)]
            (into
              [tag-name (merge (dissoc new-attrs :tag) attrs)]
              (map normalize args)))
    [[(elem :guard keyword?) & args]]
          (let [{tag-name :tag :as new-attrs} (parse-tag elem)]
            (into
              [tag-name (dissoc new-attrs :tag)]
              (map normalize args)))
    [[(elem :guard keyword?)]]
          (let [{tag-name :tag :as new-attrs} (parse-tag elem)]
            [tag-name (dissoc new-attrs :tag)])
    :else template))

