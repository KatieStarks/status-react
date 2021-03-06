(ns status-im.chat.views.input.input
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [clojure.string :as string]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [status-im.chat.constants :as constants]
            [status-im.chat.styles.input.input :as style]
            [status-im.chat.views.input.parameter-box :as parameter-box]
            [status-im.chat.views.input.result-box :as result-box]
            [status-im.chat.views.input.send-button :as send-button]
            [status-im.chat.views.input.suggestions :as suggestions]
            [status-im.chat.views.input.validation-messages :as validation-messages]
            [status-im.ui.components.animation :as animation]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.icons.vector-icons :as vi]
            [status-im.utils.utils :as utils]))

(defview basic-text-input [{:keys [set-layout-height-fn set-container-width-fn height single-line-input?]}]
  (letsubs [input-text     [:chat :input-text]
            command        [:selected-chat-command]
            input-focused? [:get-current-chat-ui-prop :input-focused?]
            input-ref      (atom nil)]
    [react/text-input
     {:ref                    #(when %
                                 (re-frame/dispatch [:set-chat-ui-props {:input-ref %}])
                                 (reset! input-ref %))
      :accessibility-label    :chat-message-input
      :multiline              (not single-line-input?)
      :default-value          (or input-text "")
      :editable               true
      :blur-on-submit         false
      :on-focus               #(re-frame/dispatch [:set-chat-ui-props {:input-focused? true}])
      :on-blur                #(re-frame/dispatch [:set-chat-ui-props {:input-focused? false}])
      :on-submit-editing      (fn [e]
                                (if single-line-input?
                                  (re-frame/dispatch [:send-current-message])
                                  (.setNativeProps @input-ref (clj->js {:text (str input-text "\n")}))))
      :on-layout              (fn [e]
                                (set-container-width-fn (.-width (.-layout (.-nativeEvent e)))))
      :on-change              (fn [e]
                                (let [native-event (.-nativeEvent e)
                                      text         (.-text native-event)
                                      content-size (.. native-event -contentSize)]
                                  (when (and (not single-line-input?)
                                             content-size)
                                    (set-layout-height-fn (.-height content-size)))
                                  (when (not= text input-text)
                                    (re-frame/dispatch [:set-chat-input-text text])
                                    (when command
                                      (re-frame/dispatch [:load-chat-parameter-box (:command command)]))
                                    (re-frame/dispatch [:update-input-data]))))
      :on-content-size-change (when (and (not input-focused?)
                                         (not single-line-input?))
                                #(let [h (-> (.-nativeEvent %)
                                             (.-contentSize)
                                             (.-height))]
                                   (set-layout-height-fn h)))
      :on-selection-change    #(let [s   (-> (.-nativeEvent %)
                                             (.-selection))
                                     end (.-end s)]
                                 (re-frame/dispatch [:update-text-selection end]))
      :style                  (style/input-view height single-line-input?)
      :placeholder-text-color colors/gray
      :auto-capitalize        :sentences}]))

(defview invisible-input [{:keys [set-layout-width-fn value]}]
  (letsubs [input-text [:chat :input-text]]
    [react/text {:style     style/invisible-input-text
                 :on-layout #(let [w (-> (.-nativeEvent %)
                                         (.-layout)
                                         (.-width))]
                               (set-layout-width-fn w))}
     (or input-text "")]))

(defview invisible-input-height [{:keys [set-layout-height-fn container-width]}]
  (letsubs [input-text [:chat :input-text]]
    [react/text {:style     (style/invisible-input-text-height container-width)
                 :on-layout #(let [h (-> (.-nativeEvent %)
                                         (.-layout)
                                         (.-height))]
                               (set-layout-height-fn h))}
     (or input-text "")]))

(defn- input-helper-view-on-update [{:keys [opacity-value placeholder]}]
  (fn [_]
    (let [to-value (if @placeholder 1 0)]
      (animation/start
        (animation/timing opacity-value {:toValue  to-value
                                         :duration 300})))))

(defview input-helper [{:keys [width]}]
  (letsubs [placeholder   [:chat-input-placeholder]
            opacity-value (animation/create-value 0)
            on-update     (input-helper-view-on-update {:opacity-value opacity-value
                                                        :placeholder   placeholder})]
    {:component-did-update on-update}
    [react/animated-view {:style (style/input-helper-view width opacity-value)}
     [react/text {:style (style/input-helper-text width)}
      placeholder]]))


(defn get-options [type]
  (case (keyword type)
    :phone {:keyboard-type "phone-pad"}
    :password {:secure-text-entry true}
    :number {:keyboard-type "numeric"}
    nil))

(defview seq-input [{:keys [command-width container-width]}]
  (letsubs [command            [:selected-chat-command]
            arg-pos            [:current-chat-argument-position]
            seq-arg-input-text [:chat :seq-argument-input-text]]
    (when (get-in command [:command :sequential-params])
      (let [{:keys [placeholder hidden type]} (get-in command [:command :params arg-pos])]
        [react/text-input (merge {:ref                 #(re-frame/dispatch [:set-chat-ui-props {:seq-input-ref %}])
                                  :style               (style/seq-input-text command-width container-width)
                                  :default-value       (or seq-arg-input-text "")
                                  :on-change-text      #(do (re-frame/dispatch [:set-chat-seq-arg-input-text %])
                                                            (re-frame/dispatch [:load-chat-parameter-box (:command command)])
                                                            (re-frame/dispatch [:set-chat-ui-props {:validation-messages nil}]))
                                  :placeholder         placeholder
                                  :accessibility-label :chat-request-input
                                  :blur-on-submit      false
                                  :editable            true
                                  :on-submit-editing   (fn []
                                                         (when-not (or (string/blank? seq-arg-input-text)
                                                                       (get-in @command [:command :hide-send-button]))
                                                           (re-frame/dispatch [:send-seq-argument]))
                                                         (utils/set-timeout
                                                           #(re-frame/dispatch [:chat-input-focus :seq-input-ref])
                                                           100))}
                                 (get-options type))]))))

(defview input-view [{:keys [single-line-input?]}]
  (letsubs [command [:selected-chat-command]]
    (let [component              (reagent/current-component)
          set-layout-width-fn    #(reagent/set-state component {:width %})
          set-layout-height-fn   #(reagent/set-state component {:height %})
          set-container-width-fn #(reagent/set-state component {:container-width %})
          {:keys [width height container-width]} (reagent/state component)]
      [react/view {:style style/input-root}
       [react/animated-view {:style (style/input-animated height)}
        [invisible-input {:set-layout-width-fn set-layout-width-fn}]
        [invisible-input-height {:set-layout-height-fn set-layout-height-fn
                                 :container-width      container-width}]
        [basic-text-input {:set-layout-height-fn   set-layout-height-fn
                           :set-container-width-fn set-container-width-fn
                           :height                 height
                           :single-line-input?     single-line-input?}]
        [input-helper {:width width}]
        [seq-input {:command-width   width
                    :container-width container-width}]]])))

(defn commands-button []
  [react/touchable-highlight
   {:on-press #(do (re-frame/dispatch [:set-chat-input-text constants/command-char])
                   (react/dismiss-keyboard!))}
   [react/view
    [vi/icon :icons/input-commands {:container-style style/input-commands-icon
                                    :color           :dark}]]])

(defview input-container []
  (letsubs [margin     [:chat-input-margin]
            input-text [:chat :input-text]
            result-box [:get-current-chat-ui-prop :result-box]]
    (let [single-line-input? (:singleLineInput result-box)]
      [react/view {:style     (style/root margin)
                   :on-layout #(let [h (-> (.-nativeEvent %)
                                           (.-layout)
                                           (.-height))]
                                 (when (> h 0)
                                   (re-frame/dispatch [:set-chat-ui-props {:input-height h}])))}
       [react/view {:style style/input-container}
        [input-view {:single-line-input? single-line-input?}]
        (when (string/blank? input-text)
          [commands-button])
        [send-button/send-button-view]]])))

(defn container []
  [react/view
   [parameter-box/parameter-box-view]
   [result-box/result-box-view]
   [suggestions/suggestions-view]
   [validation-messages/validation-messages-view]
   [input-container]])
