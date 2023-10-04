(ns clo.context
  (:gen-class)
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clo.db :as db])
  (:import [java.sql Date]))

;; Context 

(defn- max-n-characters [str n]
  (>= n (count str)))

(s/def ::stack (s/or :nil nil? :stack-coll (s/coll-of (s/and string? #(max-n-characters % 32)))))

(defn uuid
  ([] (java.util.UUID/randomUUID))
  ([value] (when value (java.util.UUID/fromString value))))

(defn- parse-stack [{:keys [stack]}]
  (if (s/valid? ::stack stack)
    (->> stack
         (filter not-empty)
         (str/join " "))
    (throw (Exception. "Invalid Stack"))))

(defn- parse-nascimento [{:keys [nascimento]}]
  (Date/valueOf nascimento))

(defn- prepare-pessoa [body-params]
  (merge body-params
         {:id (uuid)
          :stack (parse-stack body-params)
          :nascimento (parse-nascimento body-params)}))

(defn create-pessoa [body-params]
  (let [data (prepare-pessoa body-params)]
    (db/insert {:insert-into [:pessoas]
                :values [data]})))

(defn count-users []
  (-> {:select [[:%count.*]]
       :from :pessoas}
      (db/one)
      (:count)))

(defn pessoa-by-search-term [term]
  (-> {:select [:id :apelido :nome :nascimento :stack]
       :from :pessoas
       :limit 50
       :where [:ilike :search (str "%" term "%")]}
      (db/query)))

(defn pessoa-by-id [id]
  (->> {:select [:id :apelido :nome :nascimento :stack]
        :limit 1
        :from :pessoas
        :where [:= :id id]}
       (db/one)))
