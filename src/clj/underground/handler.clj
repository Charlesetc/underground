(ns underground.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [clojure.java.shell :refer [sh]]
            [hiccup.page :refer [include-js include-css html5]]
            [underground.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]))

(def mount-target
  [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-js "/js/site.js")
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

(defn loading-page [req]
  (html5
    (head)
    [:body {:class "body-container"}
     [:script (str "var csrf_token = \""
                   (get-in req [:session
                                :ring.middleware.anti-forgery/anti-forgery-token])
                   "\";")]
     mount-target
     (include-js "/js/app.js")]))


(defn markdown [e]
  (let [mdcontent (-> e :params :content)
        output (:out (sh "pandoc" :in mdcontent))]
    ; (println mdcontent output)
    output
  ))

(defroutes routes
  (GET "/" [] loading-page)
  (GET "/about" [] (loading-page))
  ; make this post with anti-forgery:
  (POST "/markdown.txt" [] markdown)
  
  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))
