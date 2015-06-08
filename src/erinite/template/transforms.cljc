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
  [template params-root params-scoped args child-xforms]
  (apply-xforms
    template
    child-xforms
    params-root
    params-scoped))


(defn content
  "Transformation that replaces content with content from params keyed by args.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     not applied
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [key]"
  [[elem attrs & content :as template] params-root params-scoped args child-xforms]
  (if-let [[key] args]
    [elem attrs (get params-scoped key)]
    template))


(defn content-global
  "Transformation that replaces content with content from params keyed by args.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     not applied
   Read params:          root
   Narrow scope:         no
   Expected args:        [key]"
  [[elem attrs & content :as template] params-root params-scoped args child-xforms]
  (if-let [[key] args]
    [elem attrs (get params-root key)]
    template))


(defn clone-for
  "Transformation that duplicates content for each item in a sequence keyed by args. 
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied
   Read params:          scoped
   Narrow scope:         yes
   Expected args:        [key]"
  [[elem attrs & content :as template] params-root params-scoped args child-xforms]
  (if-let [[key] args]
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
              child-xforms
              params-root
              item))) ; Scoped by item
        []
        (get params-scoped key)))
    template))


(defn set-attr
  "Transformation that sets an attribute to value taken from params keyed by args.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [attr-name value-key]"
  [[elem attrs & content :as template] params-root params-scoped args child-xforms]
  (if-let [[attr-name value-key] args]
    (update-in template [1] assoc attr-name (get params-scoped value-key))
    template))



(def default-transforms
  {:nop             no-op
   :content         content
   :content-globabl content-global
   :clone-for       clone-for})

