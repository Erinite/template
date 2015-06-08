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
  "Transformation that replaces content with content from params keyed by `action-arguments`.
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
  "Transformation that replaces content with content from params keyed by `action-arguments`.
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
  "Transformation that duplicates content for each item in a sequence keyed by `action-arguments`. 
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
  "Transformation that sets an attribute to value taken from params keyed by `action-arguments`.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [attr-name value-key]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply
    vector
    elem
    (if-let [[attr-name value-key] action-arguments]
      (assoc attrs attr-name (get scoped-parameters value-key))
    attrs)
    (apply-xforms
      content
      child-transformations
      parameters
      scoped-parameters)))


(defn set-class-map
  "Transformation that adds class attributes based on params keyed by `action-arguments`.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [key]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply
    vector
    elem
    (if-let [[key] action-arguments]
      (->> (get scoped-parameters key)  ; Get the class map to apply to template
           (filter val)                 ; Remove any classes that are not set to true
           (map (comp name first))      ; Get the class names as strings
           (into                        ; Append the new class names to the end of the existing classes
             (if-let [classes (:class attrs)]
               [classes]
               []))                     
           (clojure.string/join " ")    ; Join them together into a single space separated string
           (assoc attrs :class))        ; Put the class string into the attributes map
      attrs)
    (apply-xforms
      content
      child-transformations
      parameters
      scoped-parameters)))


(defn set-class
  "Transformation that sets a class based on params keyed by `action-arguments`.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [class-name should-set-key]"
  [[elem {classes :class :as attrs} & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply
    vector
    elem
    (let [[class-name should-set-key] action-arguments]
      (if-let [class-name (and class-name (name class-name))]
        (assoc
          attrs
          :class
          (if (get scoped-parameters should-set-key)
            ;; Add class if not already present
            (if-not #?(:cljs (> (.indexOf (str classes) class-name) -1)
                         :clj  (re-find (re-pattern class-name) (str classes)))
              (str classes (when classes " ") class-name)
              classes)
            ;; Remove class if not already absent
            #?(:cljs (.replace (str classes) (js/RegExp. class-name "g") "")
               :clj  (clojure.string/replace (str classes) (re-pattern class-name) ""))))
        attrs))
    (apply-xforms
      content
      child-transformations
      parameters
      scoped-parameters)))


(defn append-content
  "Transformation that appends content to the element, based on params keyed by `action-arguments`.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied (only to existing content, not to appended content)
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [key]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply
    vector
    elem
    attrs
    (conj
      (into []
        (apply-xforms
          content
          child-transformations
          parameters
            scoped-parameters))
      (when-let [[key] action-arguments]
        (get scoped-parameters key)))))


(defn prepend-content
  "Transformation that prepends content to the element, based on params keyed by `action-arguments`.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied (only to existing content, not to prepended content)
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [key]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply
    vector
    elem
    attrs
    (when-let [[key] action-arguments] (get scoped-parameters key))
    (apply-xforms
      content
      child-transformations
      parameters
      scoped-parameters)))


(defn set-element-type
  "Transformation that sets the element type based on params keyed by `action-arguments`.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [key]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply
    vector
    (if-let [[key] action-arguments]
      (get scoped-parameters key)
      elem)
    attrs
    (apply-xforms
      content
      child-transformations
      parameters
      scoped-parameters)))


(def default-transforms
  {:nop               no-op
   :content           content
   :content-global    content-global
   :clone-for         clone-for
   :set-attr          set-attr  
   :set-classes       set-class-map
   :set-class         set-class
   :append-content    append-content
   :prepend-content   prepend-content
   :set-element-type  set-element-type})

