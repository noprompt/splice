(ns p79.crdt.space.replication-clj
  (:require [p79.crdt.space :as s :refer (write q)]
            [p79.crdt.space.replication :as rep]
            [p79.crdt.space.memory :as mem :refer (in-memory)]
            [p79.read :refer (read-seq)]
            [port79.uuid :refer (random-uuid)])
  (:use clojure.test))

(deftest in-memory-agent
  (let [src (agent (in-memory))
        tgt (agent (in-memory))]
    (rep/watch-changes src (comp (partial rep/write*-to-reference tgt) rep/write-change))
    (send src s/write [{:a 5 :db/id "foo"}])
    (await src tgt)
    (let [query '{:select [?k ?v ?write ?write-time]
                  :where [["foo" ?k ?v ?write]
                          [?write ::s/write-time ?write-time]]}
          [sr] (seq (q @src query))
          [tr] (seq (q @tgt query))]
      (is (= (butlast sr) (butlast tr)))
      (is (pos? (compare (last tr) (last sr)))))))

(deftest to-disk-and-back
  (let [src (atom (in-memory))
        f (java.io.File/createTempFile "replication" ".tuples")]
    (rep/watch-changes src (comp (partial rep/tuples->disk (.getAbsolutePath f)) rep/write-change))
    (dotimes [x 1]
      (swap! src s/write [{:a x :db/id (random-uuid)}]))
    (let [replica (->> (read-seq (.getAbsolutePath f))
                    (map (partial apply s/coerce-tuple))
                    in-memory)]
      (is (= (.indexes @src) (.indexes replica))))))