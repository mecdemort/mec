
(ns mec.double-list
  (:use [clojure.contrib.def :only [defmacro-]]))

(defprotocol PDoubleList
  (get-head [this])
  (add-head [this x])
  (remove-head [this])

  (get-tail [this])
  (add-tail [this x])
  (remove-tail [this])

  (remove-node [this node])
  (add-before [this node x])
  (add-after [this node x])

  (get-nth [this n])
  (remove-nth [this n]))

(defrecord Node [prev next data])

(defn make-node
  "Create an internal or finalized node"
  ([prev next data] (Node. prev next data))
  ([m id]
     (when-let [node (get m id)]
       (assoc node :m m :id id))))

(defn get-next [node]
  (make-node (:m node) (:next node)))

(defn get-prev [node]
  (make-node (:m node) (:prev node)))

(defn- unfold [start next f]
  (for [x (iterate next start)
        :while x]
    (f x)))

(defn- seq* [m start next]
  (unfold (get m start) #(get m (next %)) :data))

(defmacro when->
  ([x pred form] `(let [x# ~x] (if ~pred (-> x# ~form) x#)))
  ([x pred form & more] `(when-> (when-> ~x ~pred ~form) ~@more)))

(deftype DoubleList [m head tail counter count mdata]
  Object
    (equals [this x]
      (when (instance? DoubleList x)
        (= m (.m x))))
    (hashCode [this] (hash (or this ())))
  clojure.lang.IObj
    (meta [_] mdata)
    (withMeta [_ mdata] (DoubleList. m head tail counter count mdata))
  clojure.lang.Sequential
  clojure.lang.Counted
    (count [_] count)
  clojure.lang.Seqable
    (seq [_] (seq* m head :next))  
  clojure.lang.Reversible
    (rseq [_] (seq* m tail :prev))
  clojure.lang.IPersistentCollection
    (empty [_] (DoubleList. (empty m) nil nil (range) 0 mdata))
    (equiv [this x] (when (sequential? x)
                      (= (seq x) (.seq this))))
    (cons [this x] (.add-head this x))
  PDoubleList
    (get-head [_] (make-node m head))
    (add-head [this x]
      (let [[i & counter] counter
            m (when-> (assoc m i (make-node nil head x))
                head (assoc-in [head :prev] i))
            tail (if tail tail i)]
        (DoubleList. m i tail counter (inc count) mdata)))
    (remove-head [this] (.remove-node this (.get-head this)))
    (get-tail [_] (make-node m tail))
    (add-tail [this x]
      (if-let [tail (.get-tail this)]
        (.add-after this tail x)
        (.add-head this x)))
    (remove-tail [this] (remove-node this (get-tail this)))
    (remove-node [this node]
      (if (get m (:id node))
        (let [{:keys [prev next id]} node
              {prev? prev, next? next} m
              head (if prev? head next)
              tail (if next? tail prev)
              m (when-> (dissoc m id)
                  prev? (assoc-in [prev :next] next)
                  next? (assoc-in [next :prev] prev))]
          (DoubleList. m head tail counter (dec count) mdata))
        this))
    (add-before [this node x]
      (if (get m (:id node))
        (let [[i & counter] counter
              {:keys [prev next id]} node
              m (when-> (-> (assoc m i (make-node prev id x))
                            (assoc-in , [id :prev] i))
                  prev (assoc-in [prev :next] i))
              head (if prev head i)]
          (DoubleList. m head tail counter (inc count) mdata))
        this))
    (add-after [this node x]
      (if (get m (:id node))
        (let [[i & counter] counter
              {:keys [prev next id]} node
              m (when-> (-> (assoc m i (make-node id next x))
                            (assoc-in , [id :next] i))
                  next (assoc-in [next :prev] i))
              tail (if next tail i)]
          (DoubleList. m head tail counter (inc count) mdata))
        this))
    (get-nth [this n]
      (let [p (< n (/ count 2))
            next (if p :next :prev)]
        (loop [n (if p n (- count n 1))
               id (if p head tail)]
          (cond (nil? id) (throw (IndexOutOfBoundsException.))
                (zero? n) (make-node m id)
                :else (recur (dec n) (get-in m [id next]))))))
    (remove-nth [this n] (.remove-node this (.get-nth this n))))

(defn double-list
  ([] (DoubleList. {} nil nil (range) 0 nil))
  ([coll]
     (reduce add-tail (double-list) coll)))

(defmethod print-method DoubleList [dl w]
  (print-method (interpose '<-> (seq dl)) w))

(defmethod print-method Node [n w]
  (print-method (symbol "#:mec.double_list.Node") w)
  (print-method (into {} (dissoc n :m)) w))
