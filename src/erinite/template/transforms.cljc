(ns erinite.template.transforms
  #?(:cljs
      (:require-macros
        [cljs.core.match.macros :refer [match]]))
  (:require
    #?(:cljs [cljs.core.match]
       :clj  [clojure.core.match :refer [match]])
    [erinite.template.compile :refer [apply-xforms]]))


(defn no-op
  "Transformation that does nothing.
   Applies to templates: all
   Child transforms:     applied
   Read params:          none
   Narrow scope:         no
   Expected args:        none"
  [template parameters scoped-parameters action-arguments child-transformations]
  (apply-xforms
    template
    child-transformations
    parameters
    scoped-parameters))


(defn content
  "Transformation that replaces content with content from params keyed by args.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     not applied
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [key]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (if-let [[key] action-arguments]
    [elem attrs (get scoped-parameters key)]
    template))


(defn content-global
  "Transformation that replaces content with content from params keyed by args.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     not applied
   Read params:          root
   Narrow scope:         no
   Expected args:        [key]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (if-let [[key] action-arguments]
    [elem attrs (get parameters key)]
    template))


(defn clone-for
  "Transformation that duplicates content for each item in a sequence keyed by args. 
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied
   Read params:          scoped
   Narrow scope:         yes
   Expected args:        [key]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (if-let [[key] action-arguments]
    (apply
      vector
      elem
      attrs
      (reduce
        (fn [children item]
          (into
            children
            (apply-xforms
              (into [] content) ;Transform relative to child
              child-transformations
              parameters
              item))) ; Scoped by item
        []
        (get scoped-parameters key)))
    template))


(defn set-attr
  "Transformation that sets an attribute to value taken from params keyed by args.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [attr-name value-key]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (if-let [[attr-name value-key] action-arguments]
    (update-in template [1] assoc attr-name (get scoped-parameters value-key))
    template))



(def default-transforms
  {:nop             no-op
   :content         content
   :content-globabl content-global
   :clone-for       clone-for})

