(ns underground.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [clojure.string :refer [lower-case]]
              [clojure.walk :refer [keywordize-keys]]
              [ajax.core :refer [POST]]
              [goog.dom :as dom]
              [goog.array :as array]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]))

(defn post [url data handler]
  (POST url {:params data
             :format :raw
             :headers {"X-CSRF-Token" js/csrf-token}
             :error-handler #(println "post error!" % %2 %3 %4 %5)
             :handler handler}))

; state that can be saved
(def gen-id (atom 2))
(def origin (atom nil))
(def positions (atom {:1 {:x 400 :y 200 :md "# hi there" :html "<h1>hi there</h1>"}
                      :2 {:x 400 :y 400 :md "# you" :html "<h1>you</h1>"}
                      }))


(def statenames {:genid gen-id
                 :origin origin
                 :positions positions})

(defn put-state [json-state]
  (let [js-state (.parse js/JSON json-state)
        clj-state (js->clj js-state)
        normal-state (keywordize-keys clj-state)]
     (for [[k v] statenames]
       (reset! v (k normal-state)))))

(defn get-state []
  (let [list-state (for [[k v] statenames] [k @v])
        dict-state (into {} list-state)
        json-state (.stringify js/JSON (clj->js dict-state))]
    json-state))


; used for temporary state
(def last-mouse-position (atom {:x 0 :y 0}))
(def menu-position (atom {:x -100 :y -100}))
(def drag-target (atom nil))
(def notes-drag (atom false))

(defn calcposition [id]
  (let [{x :x y :y} (id @positions)
        {x0 :x y0 :y} @origin
        pixels (fn [x] (str x "px"))
        ]
    {:top (-> y (+ y0) (- 2) pixels)
     :left (-> x (+ x0) (- 5) pixels)}
  ))

(defn convert-id [id]
  (str "note" (name id)))

(defn getparent [element class]
  (if (-> element .-tagName lower-case (= "body"))
    nil
    (if (-> element .-className lower-case (= class))
      element
      (-> element .-parentNode getparent))))


;; -------------------------
;; Views

; clean this up
(defn home-page []
    [:div#home-page
     {
        :on-mouse-down #(if (-> % .-target .-id (= "notes"))
                          (do (reset! last-mouse-position {:x (.-clientX %) :y (.-clientY %)})
                              (reset! notes-drag true)))
        :on-mouse-up #(reset! notes-drag false)
        :on-mouse-move #(if @notes-drag
                          (let [{lastx :x lasty :y} @last-mouse-position
                                ; midx (-> js/window .-innerWidth (/ 2))
                                ; midy (-> js/window .-innerHeight (/ 2))
                                x0 (-> % .-clientX (- lastx))
                                y0 (-> % .-clientY (- lasty))
                                x (-> @origin :x (+ (-> (/ x0 1) (min 60) (max -60))))
                                y (-> @origin :y (+ (-> (/ y0 1) (min 60) (max -60))))
                                ]
                            (reset! last-mouse-position {:x (.-clientX %) :y (.-clientY %)})
                            (swap! origin assoc :x x)
                            (swap! origin assoc :y y)
                                             ))
      }
     ; dragging things
     [:h2#title {:style {:position :relative
                         :left (:x @origin)
                         :top (:y @origin)}} "the underground narwhal"]
     [:nav#menu
      {:on-mouse-leave #(reset! menu-position {:x -1000 :y -1000})
       :style {:left (-> @menu-position :x (- 100))
               :top (-> @menu-position :y (- 80))}}
      [:a
       {:on-click #(let [my (.-clientY %)
                         mx (.-clientX %)]
                     (reset! menu-position {:x -1000 :y -1000})
                     (swap! gen-id inc)
                     (swap! positions assoc (-> @gen-id str keyword) {:x mx :y my :md "## placeholder" :html "<h2>placeholder</h2>"})
                     )}
       [:li "New"]]
      [:a [:li {:id :hoveritem
                :on-mouse-leave #(js/removeclass (.-target %))
                } "Save"]]
      [:a [:li "Load"]]
      [:a [:li "Export"]]
      [:a [:li "Import"]]
      ]
     [:div#notes
       {:on-mouse-move (fn [e] (if @drag-target
                                 (let [{x0 :x y0 :y} @origin
                                       x (.-clientX e)
                                       y (.-clientY e)
                                       x (- x x0)
                                       y (- y y0)
                                       ; x (+ x (js/get_scroll_width))
                                       ; y (+ y (js/get_scroll_height))
                                       ]
                                   (swap! positions assoc-in [@drag-target :x] x)
                                   (swap! positions assoc-in [@drag-target :y] y))
                                 ))

        :on-mouse-up (fn [e] (reset! drag-target nil))
        :class (if @drag-target "unselectable")
        :on-context-menu (fn [e]
                           (let [my (.-clientY e)
                                 mx (.-clientX e)
                                 ; not used yet but will be probably
                                 note (-> e .-target (getparent "note"))
                                 pos {:x mx :y my}]
                             (reset! menu-position pos))
                           (js/noclick e))
        }
      (doall
        (for [id (keys @positions)]
          [:div.note
             {:key id
              :style (merge (calcposition id) {:z-index (if (or (= id @drag-target) (-> @positions id :edit)) 100 1)})}
             [:div.dragbar {:on-mouse-down (fn [e] (reset! drag-target id))}]
             [:div.note-content
                {:id (convert-id id)
                 :on-click (fn [e]
                             (swap! positions assoc-in [id :edit] true)
                             (js/highlight_text_area (convert-id id)))}
                   [:textarea.editor {:value (-> @positions id :md)
                                      :on-change (fn [e]
                                                     (swap! positions assoc-in [id :md] (-> e .-target .-value)))
                                      :on-blur (fn [e]
                                                 (swap! positions assoc-in [id :edit] false)
                                                 (post "/markdown.txt" {:content (-> @positions id :md)}
                                                   #(swap! positions assoc-in [id :html] %)))
                                       :style {:display (if (-> @positions id :edit not) "none" "block")}
                               }]
                   [:span {:dangerouslySetInnerHTML {:__html (-> @positions id :html)}
                           :style {:display (if (-> @positions id :edit) "none" "block")}} ]
               ]
           ])
       )
      ; [(draggable :1)]
      ; [(draggable :2)]
     ]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
