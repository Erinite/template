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
   Expected args:        [korks]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (if-let [[korks] action-arguments]
    [elem attrs (if (vector? korks)
                  (get-in scoped-parameters korks)
                  (get scoped-parameters korks))]
    template))


(defn content-global
  "Transformation that replaces content with content from params keyed by `action-arguments`.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     not applied
   Read params:          root
   Narrow scope:         no
   Expected args:        [korks]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (if-let [[korks] action-arguments]
    [elem attrs (if (vector? korks)
                  (get-in parameters korks)  
                  (get parameters korks))]
    template))


(defn clone-for
  "Transformation that duplicates content for each item in a sequence keyed by `action-arguments`. 
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied
   Read params:          scoped
   Narrow scope:         yes
   Expected args:        [korks]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (if-let [[korks] action-arguments]
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
        (if (vector? korks)
          (get-in scoped-parameters korks)   
          (get scoped-parameters korks))))
    template))


(defn set-attr
  "Transformation that sets an attribute to value taken from params keyed by `action-arguments`.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [attr-name korks]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply
    vector
    elem
    (if-let [[attr-name korks] action-arguments]
      (assoc attrs attr-name (if (vector? korks)
                               (get-in scoped-parameters korks)  
                               (get scoped-parameters korks)))
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
   Expected args:        [korks]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply
    vector
    elem
    (if-let [[korks] action-arguments]
      (->> (if (vector? korks)          ; Get the class map to apply to template
              (get-in scoped-parameters korks)
              (get scoped-parameters korks))  
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
   Expected args:        [class-name should-set-korks]"
  [[elem {classes :class :as attrs} & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply
    vector
    elem
    (let [[class-name should-set-korks] action-arguments]
      (if-let [class-name (and class-name (name class-name))]
        (assoc
          attrs
          :class
          (if (if (vector? should-set-korks)
                (get-in scoped-parameters should-set-korks) 
                (get scoped-parameters should-set-korks))
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
   Expected args:        [korks]"
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
      (when-let [[korks] action-arguments]
        (if (vector? korks)
          (get-in scoped-parameters korks)     
          (get scoped-parameters korks))))))


(defn prepend-content
  "Transformation that prepends content to the element, based on params keyed by `action-arguments`.
   Applies to templates: [node-key attrs-map & content]
   Child transforms:     applied (only to existing content, not to prepended content)
   Read params:          scoped
   Narrow scope:         no
   Expected args:        [korks]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply
    vector
    elem
    attrs
    (when-let [[korks] action-arguments]
      (if (vector? korks)
        (get-in scoped-parameters korks)  
        (get scoped-parameters korks)))
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
   Expected args:        [korks]"
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply
    vector
    (if-let [[korks] action-arguments]
      (if (vector? korks)
        (get-in scoped-parameters korks) 
        (get scoped-parameters korks))
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

