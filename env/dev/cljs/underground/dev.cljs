(ns ^:figwheel-no-load underground.dev
  (:require [underground.core :as core]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(core/init!)
