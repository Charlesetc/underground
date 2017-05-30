(ns underground.handler
  (:require [underground.redis :as redis]
            [compojure.core :refer [GET POST rfn defroutes]]
            [compojure.route :refer [not-found resources]]
            [clojure.java.shell :refer [sh]]
            [hiccup.page :refer [include-js include-css html5]]
            [underground.middleware :refer [wrap-middleware]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [config.core :refer [env]]))

(def mount-target
  [:div#app
      [:h3#title "the underground narwhal"]
      ])

(defn head []
  [:head
   [:title "the underground narwhal"]
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
                   *anti-forgery-token*
                   ; (get-in req [:session
                   ;              :ring.middleware.anti-forgery/anti-forgery-token])
                   "\";")]
     mount-target
     (include-js "/js/app.js")]))


(defn markdown [e]
  (let [mdcontent (-> e :params :content)
        output (:out (sh "pandoc" :in mdcontent))]
    ; (println mdcontent output)
    output
  ))

(defn save-slate [e]
  (let [slate (-> e :params :slate)
        key (-> e :params :key)]
    (redis/set (str "slate/" key) slate)))

(defn get-slate [e]
  (let [key (-> e :params :key)]
    (redis/get (str "slate/" key))))

(defroutes routes
  (GET "/" [] loading-page)
  (GET "/about" [] loading-page)
  ; make this post with anti-forgery:
  (POST "/markdown.txt" [] markdown)
  (POST "/slate/save" [] save-slate)
  (POST "/slate/get" [] get-slate)
  
  (resources "/")
  (rfn [] loading-page))
  ; (not-found "Not Found"))

(def app (wrap-middleware #'routes))
