(ns erinite.template.core
  (:require
    [erinite.template.hiccup :as h]
    [erinite.template.compile :as c]
    [erinite.template.transforms :as t]))


(defn compile-template
  "Given a hiccup template and a map of transformations, compile a render func"
  [template transformations & [transforms-map]]
  (let [normalized-template (h/normalize template)]
    (c/compile
      normalized-template
      (c/compile-transforms
        (c/precompile-transforms
          normalized-template
          transformations)
        (or transforms-map t/default-transforms)) )))


(defn preprocess-template
  "Given a hiccup template and a map of transformations, preprocess to speed up 
   later compilation to render func"
  [template transformations]
  (let [normalized-template (h/normalize template)]
    {:template    normalized-template
     :transforms  (c/precompile-transforms
                    normalized-template
                    transformations)}))


(defn compile-preprocessed
  "Given a preprocessed template, compile a render func"
  [{:keys [template transforms]} & [transforms-map]]
  (c/compile
    template
    (c/compile-transforms
      transforms
      (or transforms-map t/default-transforms)))) 

