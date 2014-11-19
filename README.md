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

Curmudjeon requires Java 8 for the Nashorn Javascript engine.

**There is a bug in JDK `1.8.0_25` that causes rendering to fail. It is recommended to use the latest JDK 8 pre-release from Oracle when using Curmudjeon.**

Add the following dependency to your `project.clj` file:

```clojure
[curmudjeon "0.1.3"]
```

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

## Debugging

The minified build of React doesn't provide much information when
something goes wrong. It's possible to use the development build
by setting the `curmudjeon.devel` system property.

For example, in your `project.clj`:

```clojure
:jvm-opts ["-Dcurmudjeon.devel=1"]
```

## License

Copyright © 2014 OtherPeoplesPixels

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
