(ns underground.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [clojure.string :refer [lower-case replace]]
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
(def gen-id (atom 0))
(def origin (atom nil))
(def positions (atom {}))


(def statenames {:genid gen-id
                 :origin origin
                 :positions positions})

(defn put-state [json-state]
  (let [js-state (.parse js/JSON json-state)
        clj-state (js->clj js-state)
        normal-state (keywordize-keys clj-state)]
    (doseq [[k v] statenames]
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
(def urlkey (atom "/"))
(def clicked-note (atom nil))

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


(defn ^:export network-save-state []
  (post "/slate/save" {:slate (get-state) :key @urlkey} (fn [e] (comment in the future, check if saved)))
  )

(defn network-get-state []
  (post "/slate/get" {:key @urlkey} (fn [e] (put-state e)))
  )

(defn reset-menu-position []
  (reset! clicked-note nil)
  (reset! menu-position {:x -1000 :y -1000}))

;; -------------------------
;; Views

(defn menu []
   [:nav#menu
    {:on-mouse-leave reset-menu-position
     :on-mouse-move #(js/removeclasshoveritem)
     :style {:left (-> @menu-position :x (- 100))
             :top (-> @menu-position :y (- 80))}}
    [:a [:li {:on-click reset-menu-position
              } "Arrow"]]
    [:a [:li {:id :hoveritem
              :on-click #(let [my (.-clientY %)
                               mx (.-clientX %)
                               {ox :x oy :y} @origin
                               x (- mx ox)
                               y (- my oy)]
                           (reset-menu-position)
                           (swap! gen-id inc)
                           (swap! positions assoc
                                  (-> @gen-id str keyword)
                                  {:x x :y y :md "## placeholder" :html "<h2>placeholder</h2>"})
                           )
              :on-mouse-leave #(js/removeclasshoveritem)
              :on-mouse-move #(js/removeclasshoveritem)
              } "New"]]
    (if @clicked-note 
      [:a [:li {:on-click (fn [e] 
                            (swap! positions dissoc @clicked-note)
                            (reset-menu-position)
                            )
                } "Delete"]])
    ])



(defn notes []
  [:div#notes
   {:on-mouse-move (fn [e] (if @drag-target
                             (let [{x0 :x y0 :y} @origin
                                   x (.-clientX e)
                                   y (.-clientY e)
                                   x (- x x0)
                                   y (- y y0)
                                   ]
                               (swap! positions assoc-in [@drag-target :x] x)
                               (swap! positions assoc-in [@drag-target :y] y))
                             ))

    :on-mouse-up (fn [e] (reset! drag-target nil))
    :class (if @drag-target "unselectable")
    :on-context-menu (fn [e]
                       (let [my (.-clientY e)
                             mx (.-clientX e)
                             note (-> e .-target (getparent "note-content"))
                             note-id (if note (-> note .-id (replace "note" "") keyword))
                             pos {:x mx :y my}]
                         (reset! menu-position pos)
                         (reset! clicked-note note-id)
                         (js/noclick e)))
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
                     (if (-> e .-target .-tagName lower-case (= "a") not)
                       (do
                         (swap! positions assoc-in [id :edit] true)
                         (js/highlight_text_area (convert-id id))
                        )))}
        (if (-> @positions id :edit)
          (do
            (reagent/after-render #(js/fix_text_areas))
            [:textarea.editor {:value (-> @positions id :md)
                               :on-change (fn [e]
                                            (swap! positions assoc-in [id :md] (-> e .-target .-value)))
                               :on-blur (fn [e]
                                          (swap! positions assoc-in [id :edit] false)
                                          (post "/markdown.txt" {:content (-> @positions id :md)}
                                                #(swap! positions assoc-in [id :html] %)))
                               :style {:display (if (-> @positions id :edit not) "none" "block")}
                               }])
          [:span {:dangerouslySetInnerHTML {:__html (-> @positions id :html)}
                  :style {:display (if (-> @positions id :edit) "none" "block")}} ])
        ]
       ])
    )
   ])

(defn home-page []
    [:div#home-page
     {
        :on-mouse-down #(if (-> % .-target .-id (= "notes"))
                          (do (reset! last-mouse-position {:x (.-clientX %) :y (.-clientY %)})
                              (reset! notes-drag true)))
        :on-mouse-up #(reset! notes-drag false)
        :on-mouse-move #(if @notes-drag
                          (let [{lastx :x lasty :y} @last-mouse-position
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
                         :top (:y @origin)}}
      (if (= @urlkey "/")
        "the underground narwhal"
        ; TODO: make each section a link!
        (replace @urlkey "/" " / "))]
     [menu]
     [notes]
])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "*" [e]
  (reset! urlkey (-> js/window .-location .-pathname))
  (network-get-state)
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
