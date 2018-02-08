(ns status-im.chat.styles.input.send-button)

(def color-send "#4360df")

(defn send-message-container [rotation]
  {:background-color color-send
   :width            30
   :height           30
   :border-radius    15
   :padding          4
   :margin-left      8
   :margin-bottom    11
   :transform        [{:rotate rotation}]})

(def send-message-icon
  {:height 22
   :width  22})