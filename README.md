# erinite/template

Hiccup transformation library for Clojure and Clojurescript.

Transform hiccup templates using data-driven enlive-inspired transformations.


## Installation

[![Clojars Project](http://clojars.org/erinite/template/latest-version.svg)](http://clojars.org/erinite/template)

### Leiningen

Add the following to your `:dependencies`:


```clj
[erinite/template "0.1.0"]
```

### Maven

```xml
<dependency>
  <groupId>erinite</groupId>
  <artifactId>template</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Dependencies

Requires Clojure 1.7 or later as it uses .cljc files and reader conditionals to
compile to both Clojure and Clojurescript.

## Usage

```clj
(require '[erinite.template.core :as t])

;; A hiccup template
(def hiccup [:div
              [:div#name
                [:div.first.name] 
                [:div.last.name]]
              [:ul.details
                [:li.details
                  [:span "Details"]
                  [:a.link {:href "http://example.com"} "link"]]]
              [:div#footer]])

;; Transformation rules
(def transformations {[:div#name :.first.name]        [:content :first-name]
                      [:div#name :.last.name]         [:content :last-name]
                      [:ul.details]                   [:clone-for :details]
                      [:ul.details :li.details :span] [:content :text]})

;; Compile the template and transformation rules to create a render function
(def render-template (t/compile-template hiccup transformations))

;; Render the template
(render-template {:first-name "Bob"
                  :last-name "Smith"
                  :details [{:text "Some text"}
                            {:text "Some more text"}]})

; [:div {}
;   [:div {:id "name"}
;     [:div {:class "first name"} "Bob"]
;     [:div {:class "last name"} "Smith"]]
;   [:ul {:class "details"}
;     [:li {:class "details"}
;       [:span {} "Some text"]
;       [:a {:class "link" :href "http://example.com"} "link"]] 
;     [:li {:class "details"}
;       [:span {} "Some more text"]
;       [:a {:class "link" :href "http://example.com"} "link"]]]
;   [:div {:id "footer"}]])
```

### Transformations

Transformations are specified as a map, where the key repersents a `selector`
and the value represents an `action` to apply to the node addressed by the
selector.

Selectors are Enlive-inspired CSS-esque "paths" into the hiccup data structure
and are a vector of nodes.  `[node1 node2 node3]` is a selector that names a
hiccup node `node3` which is a child of another node `node2`, which is itself a
child of anther node `node1`. It does not matter if there are any nodes in
between the named ones.

Each node is a keyword and may take one of the following forms:

* `:element` - the hiccup node must be of type :element (eg `:div` or `:a`)
* `:#id` - the hiccup node must have the id `"id"`
* `:.class` - the hiccup node must have class `"class"`, multiple classes can be
  listed together: `:.class1.class2.class3` means that the hiccup node must have
class1, class2 and class3 set as classes.

Or any combination of these (eg `:a.button.cancel`, `:#header.banner` or
`:div#footer.contact`).

The first transformation selector in the above example, `[:div#name :.first.name]`
addresses the node `[:div.first.name]` in the sample hiccup.

Actions are vectors where the first element is a keyword naming the action and
the remaining elements are parameters passed to the transformation function. It
is possible to define your own, however the following actions are packaged with
erinite/template:

* `[:content key]` - Replace the content (that is, everything other than the
  node type and attributes map) of the node with the value found in the rendered
parameters indexed by *key*.
* `[:content-global key]` - Same as :content, but *key* is always "global" to
  the passed in parameters rather than scoped to cloned items
* `[:clone-for key]` - The node's content will be cloned for each item in the
  sequence found at *key*. This creates a new "scope" for :content (ie :content
will look up its key relative to the cloned item)
* `[:set-attr attr-name key]` - Sets the nodes *attr-name* attribute to
  value found at *key*.
* `[:set-classes key]` - Looks up map of class keywords to booleans at *key*,
  for each class whose value is truthy, adds the class to the node.
* `[:set-class class key]` - If value at *key* is truthy, then add class
  named by *class* keyword to nodes classes (or do nothing if already present).
If value at *key* is falsey, then remove the class instead (or do nothing if
already absent).
* `[:append-content key]` - Append content found at *key* to the nodes
  content.
* `[:prepend-content key]` - Prepend content found at *key* to the nodes
  content.
* `[:set-element-type key]` - Replace element type of node with element type
  keyword found at *key*. 


### Preprocessing

As erinite/template must process the pure-data transformation rules (and hiccup
tmplate) in order to produce a render function, this can be expensive for large
templates. It is therefore possible to preprocess a template to produce another
data structure which is less expensive to compile into a render function. This
could, for example, be done on the server before sending the template to the
client for rendering.

The template and transformatinos, including precompiled templates, are pure
data. This allows them to be easily transformed further, stored, transmitted or
generated programatically. However, in order to efficently apply the template to
data, it must be compiled into functions which apply the transformations. The
preprocessing step exists so that this final step can occur as late as possible.

Preprocessing templates and converting the preprocessed templates into render
functions is simple:

```clj
;; Preprocess the template and transformations
(def preprocessed-template (t/preprocess-template hiccup transformations))

;; preprocessed-template can now be sent to the client, stored in a database
;; or further processed before converting it into a render function.

;; Convert into a render function
(def render-template (t/compile-preprocessed preprocessed-template))

;; Render the template
(render-template {:first-name "Bob"
                  :last-name "Smith"
                  :details [{:text "Some text"}
                            {:text "Some more text"}]})

; [:div {}
;   [:div {:id "name"}
;     [:div {:class "first name"} "Bob"]
;     [:div {:class "last name"} "Smith"]]
;   [:ul {:class "details"}
;     [:li {:class "details"}
;       [:span {} "Some text"]
;       [:a {:class "link" :href "http://example.com"} "link"]] 
;     [:li {:class "details"}
;       [:span {} "Some more text"]
;       [:a {:class "link" :href "http://example.com"} "link"]]]
;   [:div {:id "footer"}]])

```

### Custom Actions

Both `compile-template` and `compile-preprocessed` take an optional actions-map as a
last argument. If it is omitted, then
`erinite.template.transforms/default-transforms` is used.

The action map is a map where the key is the action name (eg `:content`) and the
value is a function that applies the action to the template.

These transformation functions take the following arguments:

`[template parameters scoped-parameters action-arguments child-transformations]`

* `template` - the template node addressed by the selector for this rule (should
  be in the form `[elemen-kw attrs-map & child-nodes]`)
* `parameters` - the parameters map passed into the render function
* `scoped-parameters` - a subset of `parameters` as narrowed down by scoped
  transformations (such as `clone-for`)
* `action-arguments` - the arguments passed into the action in the rule (eg the
  key argument in :content)
* `child-transformations` - sequence of transformation functions to apply to
  this nodes children

As an example, the transformation function for :content looks like this:
```clj
(defn content
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (if-let [[key] action-arguments]
    [elem attrs (get scoped-parameters key)]
    template))
```

Note that content does not apply the child-transformations (since it overwrites
any children the node may have)

To use custom actions, you simply pass them to `compile-template` or `compile-preprocessed`:

```clj
;; Define transformation function
(defn upper-case-transformation
  [[elem attrs & content :as template] parameters scoped-parameters action-arguments child-transformations]
  (apply vector elem attrs (map clojure.string/upper-case content))

;; Create action map (possibly merging custom transformations into the default map)
(def my-custom-actions
  (assoc
    erinite.template.transforms/default-transform
    :upper-case
    upper-case-transformation))

;; Compile your templates
(def render-template (t/compile-template hiccup transformations))
(def render-template (t/compile-preprocessed preprocessed-template))

```


## Known Limitations

1. `selectors`: Unlike CSS, there is currently no way to specify that a node must be a direct child of another.
1. `transformations`: Transformations are always applied to the content of a node, so it is not possible to modify the parent (in practice this means that clone-for can clone the nodes' children but not the node itself, to do so you would have to select the parent instead)




## License

Copyright © 2015 Dan Kersten

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
