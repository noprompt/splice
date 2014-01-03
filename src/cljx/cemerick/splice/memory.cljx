(ns cemerick.splice.memory
  #+clj (:require [cemerick.splice.memory.planning :refer (plan plan* quote-symbols)])
  #+cljs (:require-macros [cemerick.splice.memory.planning :refer (plan plan*)])
  (:require [cemerick.splice :as s :refer (Space IndexedSpace)]
            [cemerick.splice.memory.query :as q]
            [cemerick.splice.hosty :refer (now)]
            [clojure.set :as set]))

(defn- add-tuples
  [indexes tuples]
  (reduce
    (fn [indexes index-keys]
      (update-in indexes [index-keys] into tuples))
    indexes
    (keys indexes)))

(deftype MemSpace [indexes as-of metadata]
  #+clj clojure.lang.IMeta
  #+clj (meta [this] metadata)
  #+cljs IMeta
  #+cljs (-meta [this] metadata)
  #+clj clojure.lang.IObj
  #+clj (withMeta [this meta] (MemSpace. indexes as-of meta))
  #+cljs IWithMeta
  #+cljs (-with-meta [this meta] (MemSpace. indexes as-of meta))
  Space
  (write* [this write-tag tuples]
    (let [tuples (cons (s/coerce-tuple write-tag s/write-time (now) write-tag) tuples)]
      (MemSpace.
        (add-tuples indexes tuples)
        as-of metadata))))

(defn in-memory
  ([] (MemSpace. q/empty-indexes nil {}))
  ([init-tuples] (MemSpace. (add-tuples q/empty-indexes init-tuples) nil {})))

;; TODO requiring query planning at compile-time is a sham. See bakery.md
(defn- ensure-planned
  [query]
  (if (-> query meta :planned)
    query
    #+clj (eval (quote-symbols (plan* query)))
    #+cljs (throw (js/Error. "Cannot plan query at runtime in ClojureScript"))))

(extend-type MemSpace
  IndexedSpace
  (index [this index-type] ((.-indexes this) index-type))
  (q* [space query arg-values]
    (let [query (ensure-planned query)
          args (zipmap (:args query) arg-values)
          matches (q/query space query #{args})]
      (->> matches
        (map (apply juxt (:select query)))
        ;; TODO we can do this statically
        (remove (partial some nil?))
        set))))
