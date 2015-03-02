(ns curmudjeon.hiccup
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [javax.script ScriptEngineManager]
           [javax.script SimpleScriptContext ScriptContext]
           [java.util.concurrent ArrayBlockingQueue]))

(declare hiccup->react)

(def dash->camel
  (memoize (fn [k]
             (let [[s & ss] (str/split (name k) #"-")]
               (keyword
                (if (#{"aria" "data"} s)
                  (name k)
                  (str/join "" (cons s (map str/capitalize ss)))))))))

(defn cleanup-attr [k]
  (case k
    :charset :charSet
    :class :className
    :for :htmlFor
    :dangerously-set-inner-html :dangerouslySetInnerHTML
    (dash->camel k)))

(defn desugar-class-set [cx]
  (when cx
    (cond
     (keyword? cx) (name cx)

     (string? cx) cx

     (set? cx)
     (->> (disj cx nil)
          (map name)
          (str/join " "))

     (vector? cx)
     (recur (set cx))

     (map? cx)
     (recur (set (for [[k v] cx] (when v k))))

     :else
     (throw (ex-info "Can't desugar class attribute" {:class cx})))))

(defn cleanup-style [[k v]]
  [(dash->camel k) v])

;; TODO more robust parsing
(defn parse-style-string [s]
  (assert (not (re-find #"\"" s))
          "Parsing style attributes containing quotes is unsupported.")
  (->> (str/split s #";")
    (mapcat #(str/split % #":"))
    (map str/trim)
    (partition 2)
    (map vec)
    (into {})))

(defn cleanup-style-map [attrs]
  (let [k :style
        style-attr (get attrs k)]
    (cond (map? style-attr)
          (->> (map cleanup-style style-attr)
            (into {})
            (assoc attrs k))

          ;; React requires style attributes to be maps
          (string? style-attr)
          (->> style-attr
            parse-style-string
            (assoc attrs k)
            recur)

          :else attrs)))

(defn cleanup-attrs [attrs]
  (-> (zipmap (->> (keys attrs)
                   (map cleanup-attr))
              (vals attrs))
      (update-in [:className] desugar-class-set)
      cleanup-style-map))

(defn merge-attrs [tag-attrs attrs]
  (let [merged (-> (cleanup-attrs attrs)
                 (update-in [:className] str " " (:class tag-attrs))
                 (merge (select-keys tag-attrs [:id])))]
    (if (re-find #"\S" (:className merged ""))
      merged
      (dissoc merged :className))))

;; Courtesy of James Reeve's Hiccup
(def re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(defn parse-tag [k]
  (assert (keyword? k)
          (str "Tags must be keywords, not " (pr-str k)))
  (let [[_ tag id classes] (re-matches re-tag (name k))]
    [tag (merge (when id {:id id})
                (when classes {:class (.replace ^String classes "." " ")}))]))

(defn parse-tag-vector [v]
  (let [[k & more] v
        [tag tag-attrs] (parse-tag k)
        [attrs nested] (if (map? (first more))
                         [(first more) (next more)]
                         [{} more])]
    [tag (merge-attrs tag-attrs attrs) nested]))

(defn de-nest-single-child [children]
  (if (and (sequential? children) (= 1 (count children)))
    (first children)
    children))

(defn tag->react [v]
  (if (fn? (v 0))
    (hiccup->react (apply (first v) (rest v)))
    (let [[tag attrs nested] (parse-tag-vector v)
          children (hiccup->react nested)
          props (if (seq children)
                  (assoc attrs :children (de-nest-single-child children))
                  attrs)]
      {:type tag
       :props props
       :_isReactElement true})))

(defn hiccup->react [h]
  (cond (string? h)
        [h]

        (or (nil? h) (and (coll? h) (empty? h)))
        nil

        (vector? h)
        [(tag->react h)]

        (seq? h)
        (mapcat hiccup->react h)

        :else
        (throw (IllegalArgumentException.
                (str "Can't convert to react form: " (pr-str h))))))

(def react-resource
  (io/resource
   (if (System/getProperty "curmudjeon.devel")
     "curmudjeon/react.dev.js"
     "curmudjeon/react.min.js")))

(defn react-engine []
  (let [n (.getEngineByMimeType (ScriptEngineManager.) "application/javascript")
        src (slurp react-resource)]
    (when (nil? n)
      (throw (RuntimeException.
              "Failed to instantiate ScriptEngine.")))
    (doto n
      (.eval "var global=this,console={log:function(){print.apply(null,Array.prototype.slice.call(arguments))}};console.warn=console.log;")
      (.eval src)
      (.eval (str "function reactRenderToString(x){"
                  "  return React.renderToStaticMarkup(JSON.parse(x));"
                  "};")))))

(def react-engines
  (let [cpus (.availableProcessors (Runtime/getRuntime))
        load-react #(delay (react-engine))]
    (->> (repeatedly load-react)
      (take cpus)
      (ArrayBlockingQueue. cpus true))))

;; Public

(defn render-to-string* [engine json]
  (.invokeFunction engine "reactRenderToString"
                   (into-array [json])))

(defn render-to-string [h]
  (assert (vector? h)
          (str "Expected hiccup-style vector, got " (pr-str h)))
  (if-let [tags-json (some-> (tag->react h) json/encode)]
    (let [engine (.take react-engines)]
      (try
        (render-to-string* @engine tags-json)
        (finally
          (.put react-engines engine))))
    ""))
