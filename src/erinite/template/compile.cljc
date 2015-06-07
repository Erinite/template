(ns erinite.template.compile
  (:refer-clojure :exclude [compile])
  #?(:cljs
      (:require-macros
        [cljs.core.match.macros :refer [match]]))
  (:require
    #?(:cljs [cljs.core.match]
       :clj  [clojure.core.match :refer [match]])
    [erinite.template.hiccup :as hiccup]))

(set! *warn-on-reflection* true)

(defn- matches-sel-pattern?
  [sel-path pattern]
  (=
   (reduce
     (fn [[[c & r :as p] counter] sel]
       (if (= (clojure.set/intersection sel c) c)
         (if (empty? r)
           (reduced (inc counter))
           [r (inc counter)])
         [p (inc counter)]))
     [pattern 0]
     sel-path)
   (count sel-path)))


(defn- generate-sel
  [elem attrs]
  (set
    (filter
      identity
      (apply
        vector
        elem
        (when-let [el-id (:id attrs)]
          (keyword (str "#" el-id)))
        (when-let [classes (:class attrs)]
          (for [el-class (clojure.string/split classes #" ")]
            (keyword (str "." el-class))))))))

(declare precompile-transforms*recursive)

(defn find-matching-xforms
  [selector-path transformations compiled-xforms idx-path children]
  (reduce
    (fn [xforms [pattern [action & params]]]
      (if (matches-sel-pattern? selector-path pattern)
        (conj xforms
              {:action    action
               :params    params
               :path      idx-path
               :children  (flatten
                            (map-indexed
                              (fn [index child]
                                (precompile-transforms*recursive
                                  child 
                                  transformations
                                  [(+ 2 index)]
                                  selector-path))
                              children))})
        xforms))
    compiled-xforms
    transformations))


(defn- precompile-transforms*sequential
  [template transformations compiled-xforms template-path selector-path continue]
  (match [template]
    [[(elem :guard keyword?) (attrs :guard map?) & args]]
          (let [selector-path (conj selector-path (generate-sel elem attrs))
                xforms (find-matching-xforms selector-path
                                             transformations
                                             compiled-xforms
                                             template-path
                                             args)
                continue  (into continue 
                                (map
                                  #(vector %2 (conj template-path %1) selector-path)
                                  (drop 2 (range))
                                  args))
                [template idx-path selector-path] (first continue)]
            (recur
              template
              transformations
              xforms
              idx-path
              selector-path
              (drop 1 continue)))
    :else (if (seq continue)
            (let [[template idx-path] (first continue)]
              (recur
                template
                transformations
                compiled-xforms
                idx-path
                selector-path
                (drop 1 continue)))
            compiled-xforms)))


(defn- precompile-transforms*recursive
  [template transformations template-path selector-path]
  (flatten
    (match [template]
      [[(elem :guard keyword?) (attrs :guard map?) & args]]
            (let [selector-path (conj selector-path (generate-sel elem attrs))
                  xforms (find-matching-xforms selector-path
                                               transformations
                                               []
                                               template-path
                                               args)]
              (if (seq xforms)
                xforms
                (map-indexed
                  (fn [index child]
                    (precompile-transforms*recursive
                      child 
                      transformations
                      (conj template-path (+ 2 index))
                      selector-path))
                  args)))
      :else nil)))


(defn- convert-selectors-to-sets
  [transformations]
  (for [[k v] transformations]
    (vector
      (for [sel k]
        (let [{:keys [tag] :as attrs} (hiccup/parse-tag sel)]
          (generate-sel tag attrs)))
      v)))


(defn precompile-transforms
  "Precompile transformations for a template.
   This will convert the transformations into a list of [paths action]
   pairs, which can be used with update-in to apply the transformations
   to the template."
  [template transformations]
  ;; Sequential performed slightly better in benchmarks, but is more
  ;; complex and therefore not a good place to implement new features
  ;; Need to also change find-matching-xforms to switch
  #_(precompile-transforms*sequential
    template
    (convert-selectors-to-sets transformations)
    []  ; compiled xforms
    []  ; template path
    []  ; selector path
    []  ; continuation
    ) 
  (precompile-transforms*recursive
    template
    (convert-selectors-to-sets transformations)
    []   ; template path
    []   ; selector path
    ))


(defn compile-transforms
  "Takes precompiled transformations and generates a list of functions which
   when applied to the template that the transformations were precompiled
   against, will apply the transformations to the template."
  [transformations action-map]
  ; TODO: validate that transformations are precompiled
  (for [{:keys [path action params children]} transformations
        update-fn (get action-map action)]
    ;; Only generate xform code if there is an update function for this action
    (when update-fn
      ;; If there are any child xforms, then bake them too
      (let [children (when children
                       (compile-transforms children action-map))]
        ;; Return a function which applies the transformation to the passed-in
        ;; template and a parameters map
        (fn [template parameters-map]
          (update-in
            template
            path
            update-fn
            parameters-map
            params
            children))))))


(defn compile
  "Takes a normalised template and compiled transformations and returns a
   functions which will apply the transformations to the template.
   Returned function takes paramteres map as argument."
  [normalized-template compiled-transformations]
  ; TODO: validate that normalized-template is in correct normalized format
  ; TODO: validate that compiled-transformations are compiled 
  (fn template-render [parameters-map]
    (reduce
      (fn [template transformation]
        (transformation template parameters-map))
      normalized-template
      compiled-transformations)))

