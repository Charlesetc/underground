
(ns underground.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [ajax.core :refer [GET POST]]
              [goog.dom :as dom]
              [goog.array :as array]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]))

(def origin (atom {:x 0 :y 0}))
(def last-mouse-position (atom {:x 0 :y 0}))
(def drag-target (atom nil))
(def notes-drag (atom false))
(def positions (atom {:1 {:x 400 :y 200 :md "# hi there" :html "<h1>hi there</h1>"}
                      :2 {:x 400 :y 400 :md "# you" :html "<h1>you</h1>"}
                      }))

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

(defn draggable [id]
  (fn [thing]
    [:div.note
     {:style (calcposition id)}
     [:div.dragbar {:on-mouse-down (fn [e] (reset! drag-target id))}]
     [:div.note-content
        {:id (convert-id id)
         :on-click (fn [e]
                     (println @positions)
                     (swap! positions assoc-in [id :edit] true)
                     (js/highlight_text_area (convert-id id)))}
             [:textarea.editor {:value (-> @positions id :md)
                                :on-change (fn [e]
                                               (println @positions)
                                               (swap! positions assoc-in [id :md] (-> e .-target .-value)))
                                :on-blur (fn [e]
                                             (swap! positions assoc-in [id :edit] false)
                                             (POST "/markdown.txt" {:params {:content (-> @positions id :md)}
                                                                    :format :raw
                                                                    :headers {"X-CSRF-Token" js/csrf-token}
                                                                    :error-handler #(println "error!" % %2 %3 %4 %5)
                                                                    :handler #(swap! positions assoc-in [id :html] %)}))
                         :style {:display (if (-> @positions id :edit not) "none" "inline")}
                         }]
           [:span {:dangerouslySetInnerHTML {:__html (-> @positions id :html)}
                   :style {:display (if (-> @positions id :edit) "none" "block")}} ]
       ]
   ]))

;; -------------------------
;; Views

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
                            (println @last-mouse-position "hi there")
                            (reset! last-mouse-position {:x (.-clientX %) :y (.-clientY %)})
                            (swap! origin assoc :x x)
                            (swap! origin assoc :y y)
                                             ))
      }
     ; dragging things
     [:h2#title "the underground narwhal"]
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
        }
       [(draggable :1)]
       [(draggable :2)]
       ; [:div.note (draggable :2 {}) [:p "you"]]
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
