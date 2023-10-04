(ns clo-web.handlers
  (:require [clo.context :as context]
            [ring.util.response :as resp]))

;; Handlers

(defn created [{:keys [body-params]}]
  (try
    (let [id (context/create-pessoa body-params)
          location (str "/pessoas/" id)]
      (resp/created location))
    (catch Exception _ (resp/status 422))))


(defn search-id [{:keys [path-params]}]
  (if-let [pessoa (some-> path-params
                          :id
                          context/uuid
                          context/pessoa-by-id)]
    (resp/response pessoa)
    (resp/status 404)))

(defn search-term [{:keys [query-params]}]
  (if-let [term (query-params "t")]
    (->
     term
     (context/pessoa-by-search-term)
     (resp/response))
    (resp/status 400)))

(defn count-users [_]
  (-> (context/count-users)
      (str)
      (resp/response)))

(defn health-check [_]
  (resp/status 200))