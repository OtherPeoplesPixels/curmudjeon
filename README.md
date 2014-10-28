# Curmudjeon

Using Curmudjeon, you can render `hiccup`-style templates using
Facebook's React library from Clojure running on the JVM.

React is great for simplifying client-side applications, but it's
often advantageous to pre-render the HTML on the server for
performance and SEO reasons. If you're using ClojureScript for your
React-based app, it's hip to run your server-side application as
ClojureScript on nodejs. Curmudjeon's for those that would rather run
Clojure on the server and reap all of the benefits of the JVM
platform.

Stay off my DOM you damn kids!

## Installation

Add the following dependency to your `project.clj` file:

```clojure
[curmudjeon "0.1.1"]
```

PLEASE NOTE: Curmudjeon requires Java 8 for the Nashorn Javascript engine.

## Usage

```clojure
(ns app.server
  (:require [curmudjeon.hiccup :refer [render-to-string]]
            [compojure.core :refer [defroutes GET]]))
  
(defn header [txt]
  [:h1 (str "Some items for " txt)])
  
(defn item-list [xs]
  [:ul#items
   (for [x xs]
    [:li x])])
    
(defn page [who]
  [:html 
   [:head [:title "A page"]]
   [:body
    (header who)
    (item-list ["First" "Another" "Last one"])]])
    
(defroutes app
  (GET "/list/:who" [who] (render-to-string (page who))))
```

## License

Copyright © 2014 OtherPeoplesPixels

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
