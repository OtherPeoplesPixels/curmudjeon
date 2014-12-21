# Curmudjeon

**NOT RECOMMENDED FOR PRODUCTION USE**

**Unfortunately, we have found that the Nashorn Javascript engine is
too buggy to rely on at present. After tracking down several errors
attributed to Javascript objects not having the correct fields or
methods being dispatched incorrectly, we have shelved this project
for the present. Instead, we recommend using a simple node-based
HTTP server to execute server-side React.**


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

Add the following dependency to your `project.clj` file:

```clojure
[curmudjeon "0.1.4"]
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
