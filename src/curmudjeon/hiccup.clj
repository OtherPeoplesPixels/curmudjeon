(ns curmudjeon.hiccup
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [javax.script ScriptEngineManager]
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

(defn cleanup-style-map [attrs]
  (if-let [style-map (:style attrs)]
    (->> (map cleanup-style style-map)
         (into {})
         (assoc attrs :style))
    attrs))

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

(defn tag->react [v]
  (if (fn? (v 0))
    (hiccup->react (apply (first v) (rest v)))
    (let [[tag attrs nested] (parse-tag-vector v)]
      (str "React.DOM." tag "("
           (->> (hiccup->react nested)
                (cons (json/encode attrs))
                (interpose "," )
                (apply str))
           ")"))))

(def react-path
  (if (System/getProperty "curmudjeon.devel")
    "curmudjeon/react.dev.js"
    "curmudjeon/react.min.js"))

(def threadsafe-react
  (let [react-url (io/resource react-path)]
    (fn [nashorn]
      (.eval nashorn
             "(function(){return loadWithNewGlobal(url)})()"
             (doto (.createBindings nashorn)
               (.put "url" react-url))))))

(defn hiccup->react [h]
  (cond (string? h)
        [(pr-str h)]

        (or (nil? h) (and (coll? h) (empty? h)))
        nil

        (vector? h)
        [(tag->react h)]

        (seq? h)
        (mapcat hiccup->react h)

        :else
        (throw (IllegalArgumentException.
                (str "Can't convert to react form: " (pr-str h))))))

(def nashorn-engine
  (let [n (.getEngineByName (ScriptEngineManager.) "nashorn")]
    (when (nil? n)
      (throw (RuntimeException.
              "Failed to instantiate Nashorn ScriptEngine (Java < 8?)")))
    n))

(def react-instances
  (let [cpus (.availableProcessors (Runtime/getRuntime))
        load-react #(delay (threadsafe-react nashorn-engine))]
    (->> (repeatedly load-react)
         (take cpus)
         (ArrayBlockingQueue. cpus true))))

(defn eval-with-bindings [engine expr & bindings]
  (let [js-bindings (.createBindings engine)
        _ (doseq [[n v] (partition 2 bindings)]
            (.put js-bindings (name n) v))]
    (.eval engine expr js-bindings)))

;; Public

(defn render-to-string [h]
  (assert (vector? h)
          (str "Expected hiccup-style vector, got " (pr-str h)))
  (if-let [dom (tag->react h)]
    (let [react (.take react-instances)]
      (try
        (eval-with-bindings nashorn-engine
                            (str "React.renderToString(" dom ")")
                            :React @react)
        (finally (.put react-instances react))))
    ""))
