(ns clo-web.router
  (:require [muuntaja.core :as m]
            [reitit.coercion.spec :refer [coercion]]
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [clo-web.handlers :as handlers]))

;; Router

(def router-config
  {:exception pretty/exception
   :data {:coercion coercion
          :muuntaja m/instance
          :middleware [;; Put :query-params in the request, otherwise is just :query-string
                       parameters/parameters-middleware
                         ;; Put :body-params in the request, otherwise is just :body
                       muuntaja/format-negotiate-middleware
                         ;; Not required for the example since we don't return bodies
                       muuntaja/format-response-middleware
                         ;; Required to parse body into :body-params
                       muuntaja/format-request-middleware
                       (exception/create-exception-middleware
                        {::exception/default (partial exception/wrap-log-to-console
                                                      exception/default-handler)})]}})

(def routes [["/" {:get handlers/health-check}]
             ["/pessoas" {:post handlers/created
                          :get handlers/search-term}]
             ["/pessoas/:id" {:get handlers/search-id}]
             ["/contagem-pessoas" {:get handlers/count-users}]])

(def router
  (-> routes
      (ring/router router-config)
      (ring/ring-handler)))