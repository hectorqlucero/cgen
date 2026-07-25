(ns cgen.tabgrid.render
  "Responsive TabGrid rendering for generated CRUD pages."
  (:require
   [clojure.string :as str]
   [hiccup.util :refer [raw-string]]
   [ring.util.codec :as ring-codec]
   [cgen.engine.config :as config]
   [cgen.i18n.core :as i18n]
   [cgen.models.crud :as crud]
   [cgen.web.csrf :refer [csrf-field]]))

(defn get-record-id [entity-name row]
  (try
    (let [pk (:primary-key (config/get-entity-config entity-name))]
      (if (and pk (vector? pk) (> (count pk) 1))
        (str/join "|" (map #(get row %) pk))
        (:id row)))
    (catch Exception _ (:id row))))

(def ^:private image-exts #{"jpg" "jpeg" "png" "gif" "bmp" "webp" "svg"})

(defn- file-extension [filename]
  (when (and filename (string? filename))
    (let [parts (str/split filename #"\.")]
      (when (> (count parts) 1)
        (str/lower-case (last parts))))))

(defn- field-value [value]
  (cond
    (and (string? value) (re-find #"^<" value)) (raw-string value)
    (nil? value) [:span.text-muted "—"]
    (and (string? value)
         (seq value)
         (contains? image-exts (file-extension value)))
    (let [path (str (:path crud/config) value "?" (random-uuid))]
      [:img.rounded.shadow-sm.border
       {:src path :alt value
        :style "max-height: 52px; width: auto; object-fit: cover; cursor: pointer; transition: transform 0.15s ease, box-shadow 0.15s ease;"
        :onmouseover "this.style.transform='scale(1.15)';this.style.boxShadow='0 4px 12px rgba(0,0,0,0.2)'"
        :onmouseout "this.style.transform='scale(1)';this.style.boxShadow=''"
        :onclick "window.open(this.src, '_blank')"}])
    :else value))

(def ^:private field-type-icon
  {:text "bi-type"
   :email "bi-envelope"
   :number "bi-123"
   :decimal "bi-currency-exchange"
   :date "bi-calendar"
   :datetime "bi-calendar-date"
   :radio "bi-ui-radios"
   :checkbox "bi-check-square"
   :select "bi-menu-button-wide"
   :textarea "bi-textarea-resize"
   :file "bi-file-earmark"
   :pdf "bi-file-earmark-pdf"
   :document "bi-file-earmark-word"
   :hidden "bi-eye-slash"
   :fk "bi-arrow-repeat"
   :imagen "bi-image"
   :image "bi-image"
   :phone "bi-telephone"
   :url "bi-link"
   :currency "bi-currency-dollar"
   :percentage "bi-percent"})

(def ^:private field-type-color
  {:text "text-body"
   :email "text-primary"
   :number "text-info"
   :decimal "text-info"
   :date "text-warning"
   :datetime "text-warning"
   :radio "text-secondary"
   :checkbox "text-success"
   :select "text-secondary"
   :textarea "text-secondary"
   :file "text-danger"
   :pdf "text-danger"
   :document "text-primary"
   :hidden "text-muted"
   :fk "text-secondary"
   :imagen "text-success"
   :image "text-success"
   :phone "text-success"
   :url "text-primary"
   :currency "text-warning"
   :percentage "text-info"})

(defn- row-label [fields row]
  (or (some (fn [[fid _]]
              (when (not= fid :id)
                (let [v (get row fid)]
                  (when (and (string? v) (seq v)) v))))
            fields)
      (str "#" (:id row))))

(defn- row-label-and-rest [fields row]
  (let [label-field (first (filter (fn [[fid _]]
                                     (and (not= fid :id)
                                          (let [v (get row fid)]
                                            (and (string? v) (seq v)))))
                                   fields))]
    [label-field (rest (drop-while #(not= label-field %) fields))]))

(defn- row-subtitle [fields row]
  (let [[_label remaining] (row-label-and-rest fields row)]
    (some (fn [[fid _]]
            (when (not= fid :id)
              (let [v (get row fid)]
                (when (and (string? v) (seq v)) v))))
          remaining)))

(defn- render-record-list [entity-name fields all-rows selected-id actions]
  (let [list-id   (str "record-list-" entity-name)
        search-id (str list-id "-search")
        count-id  (str list-id "-count")
        total     (count all-rows)
        new-url   (when (:new actions)
                    (str "/admin/" entity-name "/add-form"))]
    [:div.card.mb-4.shadow-sm.border-0.tg-list-card
     [:div.card-header.border-0.pb-0
      [:div.d-flex.justify-content-between.align-items-center.mb-3
       [:div.d-flex.align-items-center.gap-2
        [:h5.mb-0.fw-bold (i18n/tr :tabgrid/record-list)]
        [:span.badge.rounded-pill.bg-primary-subtle.text-primary-emphasis.fw-semibold
         {:id count-id} (str total)]]
       (when new-url
         [:a.btn.btn-primary.btn-sm.px-3.fw-semibold.shadow-sm.d-none.d-sm-inline-flex
          {:href new-url}
          [:i.bi.bi-plus-lg.me-1] (i18n/tr :common/new)])]
      ;; ── Search bar with Ctrl+K hint ──
      [:div.input-group.input-group-sm.tg-search-group
       [:span.input-group-text.bg-light.border-end-0
        [:i.bi.bi-search.text-muted]]
       [:input.form-control.bg-light.border-start-0
        {:type "text"
         :id search-id
         :placeholder (i18n/tr :common/search)
         :style "border-left: 0;"
         :oninput (str "var q=this.value.toLowerCase();"
                       "var items=document.getElementById('" list-id "').querySelectorAll('.list-group-item[data-id]');"
                       "var c=0;"
                       "for(var i=0;i<items.length;i++){"
                       "var show=items[i].textContent.toLowerCase().indexOf(q)!==-1;"
                       "items[i].style.display=show?'':'none';"
                       "if(show)c++;}"
                       "var ct=document.getElementById('" count-id "');"
                       "if(ct)ct.textContent=c;")}]
       ;; Ctrl+K hint (hidden on mobile)
       [:span.tg-kbd-hint.d-none.d-lg-inline-flex "Ctrl+K"]
       [:button.btn.btn-outline-secondary.border-start-0
        {:type "button"
         :onclick (str "this.previousElementSibling.previousElementSibling.value='';"
                       "this.previousElementSibling.previousElementSibling.dispatchEvent(new Event('input'));"
                       "this.previousElementSibling.previousElementSibling.focus()")}
        [:i.bi.bi-x-lg]]]]
     ;; ── Scroll wrapper ──
     [:div.tg-list-scroll
      {:id list-id}
      [:div.list-group.border-0
       (for [row all-rows
             :let [rid (str (get-record-id entity-name row))
                   active? (= rid selected-id)]
             :when row]
         [:a.list-group-item.list-group-item-action.py-3
          {:href (str "/admin/" entity-name "/" rid)
           :class (when active? "active")
           :data-id rid}
          [:div.d-flex.justify-content-between.align-items-center
           [:div.me-2.min-w-0
            [:div.fw-semibold.text-truncate (row-label fields row)]
            (when-let [subtitle (row-subtitle fields row)]
              [:div.text-muted.small.text-truncate.mt-1 subtitle])]
           [:span.badge.rounded-pill.bg-secondary-subtle.text-secondary-emphasis.flex-shrink-0.ms-2
            (str "#" rid)]]])
       (when (zero? total)
         [:div.text-center.py-5.tg-empty-state
          [:div.d-inline-flex.align-items-center.justify-content-center.rounded-3.bg-body-tertiary.mb-3
           {:style "width: 64px; height: 64px;"}
           [:i.bi.bi-inbox.fs-1.text-body-tertiary]]
          [:h6.fw-semibold.text-muted.mb-1 (i18n/tr :grid/no-records)]
          (when new-url
            [:a.btn.btn-sm.btn-outline-primary.px-3.fw-semibold.mt-2
             {:href new-url}
             [:i.bi.bi-plus-circle.me-1] (i18n/tr :common/new)])])]]
     ;; ── Mobile FAB: "New" button ──
     (when new-url
       [:a.tg-fab.d-sm-none
        {:href new-url :title (i18n/tr :common/new)}
        [:i.bi.bi-plus-lg]])]))

(defn- tabgrid-js [entity-name search-id]
  [:script
   (str
    "(function(){"
    ;; ── Ctrl+K shortcut ──
    "document.addEventListener('keydown',function(e){"
    "if((e.ctrlKey||e.metaKey)&&e.key==='k'){"
    "e.preventDefault();"
    "var s=document.getElementById('" search-id "');"
    "if(s){s.focus();s.select();}"
    "}"
    "});"
    ;; ── Keyboard nav in record list ──
    "document.getElementById('" search-id "').addEventListener('keydown',function(e){"
    "var list=document.getElementById('record-list-" entity-name "');"
    "var items=Array.from(list.querySelectorAll('.list-group-item[data-id]:not([style*=\"display: none\"]'));"
    "var cur=items.findIndex(function(i){return i===document.activeElement||i===e.target.closest('.list-group-item');});"
    "if(e.key==='ArrowDown'){e.preventDefault();var n=cur<items.length-1?cur+1:0;items[n].focus();}"
    "if(e.key==='ArrowUp'){e.preventDefault();var p=cur>0?cur-1:items.length-1;items[p].focus();}"
    "if(e.key==='Enter'&&document.activeElement.classList.contains('list-group-item')){"
    "document.activeElement.click();}"
    "});"
    ;; ── Mobile auto-scroll to detail ──
    "var dt=document.querySelector('.tg-entity-header');"
    "if(dt&&window.innerWidth<768){"
    "setTimeout(function(){dt.scrollIntoView({behavior:'smooth',block:'start'});},150);"
    "}"
    ;; ── Subgrid persistent search ──
    "function filterSubgrid(key){"
    "var tbl=document.querySelector('.tg-sg-table[data-sg-key=\"'+key+'\"]');"
    "if(!tbl)return;"
    "var input=document.querySelector('.tg-sg-search[data-sg-key=\"'+key+'\"]');"
    "var clear=document.querySelector('.tg-sg-clear[data-sg-key=\"'+key+'\"]');"
    "var countEl=document.querySelector('.tg-sg-count[data-sg-key=\"'+key+'\"]');"
    "var q=(input.value||'').toLowerCase().trim();"
    "var rows=Array.from(tbl.querySelectorAll('tbody tr'));"
    "var shown=0;"
    "rows.forEach(function(r){"
    "var txt=r.textContent.toLowerCase();"
    "var match=!q||txt.indexOf(q)!==-1;"
    "r.style.display=match?'':'none';"
    "if(match)shown++;"
    "});"
    "if(clear)clear.style.display=q?'':'none';"
    "if(countEl){"
    "if(q){countEl.textContent=shown+'/'+rows.length;}"
    "else{countEl.textContent='';}"
    "}"
    "}"
    "document.querySelectorAll('.tg-sg-search').forEach(function(input){"
    "var key=input.getAttribute('data-sg-key');"
    "var saved=sessionStorage.getItem('sgf_'+key);"
    "if(saved){input.value=saved;}"
    "filterSubgrid(key);"
    "input.addEventListener('input',function(){"
    "sessionStorage.setItem('sgf_'+key,input.value);"
    "filterSubgrid(key);"
    "});"
    "});"
    "document.querySelectorAll('.tg-sg-clear').forEach(function(btn){"
    "btn.addEventListener('click',function(){"
    "var key=btn.getAttribute('data-sg-key');"
    "var input=document.querySelector('.tg-sg-search[data-sg-key=\"'+key+'\"]');"
    "if(input){input.value='';sessionStorage.removeItem('sgf_'+key);filterSubgrid(key);input.focus();}"
    "});"
    "});"
    "})()")])

(defn- render-entity-summary [entity-name title fields row actions]
  (let [display-name (or (some-> (first fields) key row str) title)]
    [:div.card.mb-4.shadow-sm.border-0.overflow-hidden.tg-entity-header
     ;; ── Header with accent bar ──
     [:div.card-header.border-0.pb-0.pt-4.px-4
      {:style "background: linear-gradient(135deg, rgba(13,110,253,0.05) 0%, rgba(108,117,125,0.02) 100%);"}
      [:div.d-flex.justify-content-between.align-items-start.flex-column.flex-md-row.g-3
       [:div.d-flex.align-items-center.gap-3
        [:div.d-flex.align-items-center.justify-content-center.rounded-3.shadow-sm
         {:style "width: 48px; height: 48px; background: linear-gradient(135deg, var(--tg-primary) 0%, color-mix(in srgb, var(--tg-primary) 60%, #6610f2) 100%);"}
         [:i.bi.bi-folder2-open.text-white.fs-5]]
        [:div
         [:h4.fw-bold.mb-0.ls-tight (str display-name)]
         [:span.badge.bg-secondary-subtle.text-secondary-emphasis.rounded-pill.fs-7.mt-1
          (str "#" (get-record-id entity-name row))]]]
       [:div.d-flex.flex-wrap.gap-2
        (when (:edit actions)
          [:a.btn.btn-primary.btn-sm.px-3.fw-semibold.shadow-sm
           {:href (str "/admin/" entity-name "/edit-form/" (get-record-id entity-name row))}
           [:i.bi.bi-pencil-square.me-1] (i18n/tr :common/edit)])
        (when (:delete actions)
          [:form.d-inline {:method "POST" :action (str "/admin/" entity-name "/delete/" (get-record-id entity-name row))
                           :onsubmit (str "return confirm('" (i18n/tr :confirm/delete) "')")}
           (csrf-field)
           [:button.btn.btn-sm.btn-outline-danger.px-3.fw-semibold
            {:type "submit"}
            [:i.bi.bi-trash3.me-1] (i18n/tr :common/delete)]])]]]
     ;; ── Fields grid ──
     [:div.card-body.pt-3.pb-4.px-4
      [:div.row.g-3
       (for [[field-id label] fields
             :let [val (get row field-id)
                   icon (field-type-icon field-id)
                   color (field-type-color field-id)]]
         [:div.col-12.col-md-6
          [:div.border.rounded-3.p-3.h-100.field-card-hover
           [:div.d-flex.align-items-center.gap-2.mb-1
            [:i.bi {:class [icon color] :style "font-size: 0.8rem;"}]
            [:div.text-uppercase.text-muted.fs-7.fw-semibold label]]
           [:div.fs-6 {:style "min-height: 1.5em; word-break: break-word;"} (field-value val)]]])]]]))

(defn- pivot-fields?
  [through-table parent-fk related-fk]
  (try
    (let [cfg     (config/get-entity-config through-table)
          fk-ids  #{parent-fk related-fk}
          visible (remove #(or (= :hidden (:type %))
                               (contains? fk-ids (:id %)))
                          (:fields cfg))]
      (seq visible))
    (catch Exception _ false)))

(defn- m2m-action-btns
  [subgrid parent-id record return-url]
  (let [through-table (:through-table subgrid)
        parent-fk     (name (:foreign-key subgrid))
        related-fk    (name (:related-fk subgrid))
        related-id    (:id record)]
    [:div.d-flex.gap-1
     (when (pivot-fields? through-table
                          (keyword parent-fk) (keyword related-fk))
       [:a.btn.btn-outline-secondary.btn-sm.rounded-pill
        {:href (str "/tabgrid/pivot-form"
                    "?through_table=" (name through-table)
                    "&parent_fk=" parent-fk
                    "&parent_id=" parent-id
                    "&related_fk=" related-fk
                    "&related_id=" related-id
                    "&return_url=" (ring-codec/url-encode return-url))
         :title (i18n/tr :common/edit)}
        [:i.bi.bi-sliders]])
     [:form.d-inline
      {:method "POST"
       :action "/tabgrid/dissociate"}
      (csrf-field)
      [:input {:type "hidden" :name "through_table" :value (name through-table)}]
      [:input {:type "hidden" :name "parent_fk" :value parent-fk}]
      [:input {:type "hidden" :name "parent_id" :value (str parent-id)}]
      [:input {:type "hidden" :name "related_fk" :value related-fk}]
      [:input {:type "hidden" :name "related_id" :value (str related-id)}]
      [:input {:type "hidden" :name "return_url" :value return-url}]
      [:button.btn.btn-outline-danger.btn-sm.rounded-pill
       {:type "submit"
        :onclick "return confirm('¿Desvincular?')"
        :title (i18n/tr :subgrid/unlink)}
       [:i.bi.bi-x-lg]]]]))

(defn- subgrid-search-bar
  "Renders a persistent search input for a subgrid table. Keyed by sg-key."
  [sg-key]
  [:div.px-3.py-2.tg-sg-search-bar.d-flex.align-items-center.gap-2
   [:div.input-group.input-group-sm
    [:span.input-group-text.border-0.bg-body-tertiary.text-muted
     [:i.bi.bi-search {:style "font-size: 0.8rem;"}]]
     [:input.form-control.border-0.bg-body-tertiary.tg-sg-search
     {:type "text"
      :placeholder (i18n/tr :subgrid/search)
      :data-sg-key sg-key
      :aria-label (i18n/tr :subgrid/search)}]
    [:button.btn.btn-outline-secondary.border-0.tg-sg-clear
     {:type "button"
      :data-sg-key sg-key
      :title (i18n/tr :subgrid/clear-filter)
      :style "display:none;"}
     [:i.bi.bi-x-lg]]]
   [:small.text-muted.tg-sg-count {:data-sg-key sg-key :style "white-space:nowrap;"}]])

(defn- render-m2m-card
  [entity-name parent-id subgrid]
  (let [sg-fields  (:fields subgrid)
        return-url (str "/admin/" entity-name "/" parent-id)
        through     (name (:through-table subgrid))
        parent-fk   (name (:foreign-key subgrid))
        related-fk  (name (:related-fk subgrid))
        related-ent (name (:related-entity subgrid))]
    [:div.card.mb-3.shadow-sm.border-0.overflow-hidden.tg-subgrid-m2m
     [:div.card-header.d-flex.justify-content-between.align-items-center.py-3.border-0
      {:style "background: linear-gradient(135deg, rgba(13,110,253,0.04) 0%, rgba(111,66,193,0.04) 100%);"}
      [:div.d-flex.align-items-center.gap-2
       [:div.bg-primary-subtle.rounded-2.d-flex.align-items-center.justify-content-center.shadow-sm
        {:style "width: 40px; height: 40px;"}
        [:i.bi.text-primary-emphasis.fs-5 {:class (:icon subgrid)}]]
       [:div
        [:div.d-flex.align-items-center.gap-2
         [:h6.mb-0.fw-semibold (:title subgrid)]
         [:span.badge.rounded-pill.bg-primary-subtle.text-primary-emphasis.fs-7.fw-semibold "M:M"]]
        [:small.text-muted (str (or (:count subgrid) 0) " " (i18n/tr :subgrid/linked))]]]
      [:a.btn.btn-sm.btn-primary.px-3.fw-semibold.shadow-sm
       {:href (str "/tabgrid/link-form"
                   "?through_table=" through
                   "&parent_fk=" parent-fk
                   "&parent_id=" parent-id
                   "&related_entity=" related-ent
                   "&related_fk=" related-fk
                   "&return_url=" (ring-codec/url-encode return-url))}
       [:i.bi.bi-link-45deg.me-1] (i18n/tr :subgrid/link)]]
     [:div.card-body.p-0
      (when (seq (:records subgrid))
        (subgrid-search-bar (str entity-name "-" related-ent)))
      (if (seq (:records subgrid))
        [:div.table-responsive
         [:table.table.table-hover.mb-0.tg-sg-table
          {:data-sg-key (str entity-name "-" related-ent)}
          [:thead.bg-body-tertiary
           [:tr
            (for [[fid label] sg-fields]
              [:th.fw-semibold.text-uppercase.fs-7.text-muted.px-3 label])
            [:th.text-end.fw-semibold.text-uppercase.fs-7.text-muted.px-3 (i18n/tr :common/actions)]]]
          [:tbody
           (for [record (:records subgrid)
                 :let [rid (:id record)]]
             ^{:key rid}
             [:tr
              (for [[fid label] sg-fields]
                [:td.px-3.py-2 {:data-label label} (field-value (get record fid))])
              [:td.text-end.text-nowrap.px-3.py-2 {:data-label (i18n/tr :common/actions)}
               (m2m-action-btns subgrid parent-id record return-url)]])]]]
        [:div.text-center.py-5.tg-empty-state
         [:div.d-inline-flex.align-items-center.justify-content-center.rounded-3.bg-primary-subtle.mb-3
          {:style "width: 64px; height: 64px;"}
          [:i.bi.bi-link-45deg.fs-1.text-primary-emphasis]]
         [:h6.fw-semibold.text-muted.mb-1 (i18n/tr :grid/no-records)]
         [:a.btn.btn-sm.btn-outline-primary.px-3.fw-semibold.mt-2
          {:href (str "/tabgrid/link-form"
                      "?through_table=" through
                      "&parent_fk=" parent-fk
                      "&parent_id=" parent-id
                      "&related_entity=" related-ent
                      "&related_fk=" related-fk
                      "&return_url=" (ring-codec/url-encode return-url))}
          [:i.bi.bi-link-45deg.me-1] (i18n/tr :subgrid/link)]])]]))

(defn- render-1to1-card
  [entity-name parent-id subgrid]
  (let [sg-entity  (:entity subgrid)
        sg-name    (name sg-entity)
        sg-fields  (:fields subgrid)
        actions    (:actions subgrid)
        record     (:record subgrid)
        return-url (str "/admin/" entity-name "/" parent-id)]
    [:div.card.mb-3.shadow-sm.border-0.overflow-hidden.tg-subgrid-1to1
     [:div.card-header.d-flex.justify-content-between.align-items-center.py-3.border-0
      {:style "background: linear-gradient(135deg, rgba(13,202,240,0.06) 0%, rgba(108,117,125,0.03) 100%);"}
      [:div.d-flex.align-items-center.gap-2
       [:div.bg-info-subtle.rounded-2.d-flex.align-items-center.justify-content-center.shadow-sm
        {:style "width: 40px; height: 40px;"}
        [:i.bi.text-info-emphasis.fs-5 {:class (:icon subgrid)}]]
       [:div
        [:div.d-flex.align-items-center.gap-2
         [:h6.mb-0.fw-semibold (:title subgrid)]
         [:span.badge.rounded-pill.bg-info-subtle.text-info-emphasis.fs-7.fw-semibold "1:1"]]
        [:small.text-muted (if record (i18n/tr :subgrid/has-record) (i18n/tr :subgrid/no-record))]]]
      (when (and (:new actions) (not record))
        [:a.btn.btn-sm.btn-info.px-3.fw-semibold.text-white.shadow-sm
         {:href (str "/admin/" sg-name "/add-form/" parent-id
                     "?return_url=" (ring-codec/url-encode return-url)
                     "&parent_entity=" entity-name)}
         [:i.bi.bi-plus.me-1] (i18n/tr :common/new)])]
     [:div.card-body.p-0
      (if record
        [:div.table-responsive
         [:table.table.table-hover.mb-0
          [:thead.bg-body-tertiary
           [:tr
            (for [[fid label] sg-fields]
              [:th.fw-semibold.text-uppercase.fs-7.text-muted.px-3 label])
            (when (:edit actions)
              [:th.text-end.fw-semibold.text-uppercase.fs-7.text-muted.px-3 (i18n/tr :common/actions)])]]
          [:tbody
           [:tr
            (for [[fid label] sg-fields]
              [:td.px-3.py-2 {:data-label label} (field-value (get record fid))])
            (when (:edit actions)
              [:td.text-end.text-nowrap.px-3.py-2 {:data-label (i18n/tr :common/actions)}
               [:a.btn.btn-outline-primary.btn-sm.rounded-pill
                {:href (str "/admin/" sg-name "/edit-form/" (:id record)
                            "?return_url=" (ring-codec/url-encode return-url))
                 :title (i18n/tr :common/edit)}
                [:i.bi.bi-pencil.me-1] (i18n/tr :common/edit)]])]]]]
        [:div.text-center.py-5.tg-empty-state
         [:div.d-inline-flex.align-items-center.justify-content-center.rounded-3.bg-info-subtle.mb-3
          {:style "width: 64px; height: 64px;"}
          [:i.bi.bi-inbox.fs-1.text-info-emphasis]]
         [:h6.fw-semibold.text-muted.mb-1 (i18n/tr :grid/no-records)]
         (when (:new actions)
           [:a.btn.btn-sm.btn-outline-info.px-3.fw-semibold.mt-2
            {:href (str "/admin/" sg-name "/add-form/" parent-id
                        "?return_url=" (ring-codec/url-encode return-url)
                        "&parent_entity=" entity-name)}
            [:i.bi.bi-plus-circle.me-1] (i18n/tr :common/new)])])]]))

(defn- action-btns-1toN
  [sg-name actions return-url record]
  (let [rid (:id record)]
    [:div.d-flex.gap-1
     (when (:edit actions)
       [:a.btn.btn-outline-primary.btn-sm.rounded-pill
        {:href (str "/admin/" sg-name "/edit-form/" rid
                    "?return_url=" (ring-codec/url-encode return-url))
         :title (i18n/tr :common/edit)}
        [:i.bi.bi-pencil.me-1] (i18n/tr :common/edit)])
     (when (:delete actions)
       [:form.d-inline
        {:method "POST"
         :action (str "/admin/" sg-name "/delete/" rid)}
        (csrf-field)
        [:input {:type "hidden" :name "return_url" :value return-url}]
        [:button.btn.btn-outline-danger.btn-sm.rounded-pill
         {:type "submit" :onclick "return confirm('¿Está seguro?')"
          :title (i18n/tr :common/delete)}
         [:i.bi.bi-trash3.me-1] (i18n/tr :common/delete)]])]))

(defn- render-1toN-card
  [entity-name parent-id subgrid]
  (let [sg-entity  (:entity subgrid)
        sg-name    (name sg-entity)
        sg-fields  (:fields subgrid)
        actions    (:actions subgrid)
        return-url (str "/admin/" entity-name "/" parent-id)
        action-btns (partial action-btns-1toN sg-name actions return-url)]
    [:div.card.mb-3.shadow-sm.border-0.overflow-hidden.tg-subgrid-1toN
     [:div.card-header.d-flex.justify-content-between.align-items-center.py-3.border-0
      {:style "background: linear-gradient(135deg, rgba(25,135,84,0.06) 0%, rgba(108,117,125,0.03) 100%);"}
      [:div.d-flex.align-items-center.gap-2
       [:div.bg-success-subtle.rounded-2.d-flex.align-items-center.justify-content-center.shadow-sm
        {:style "width: 40px; height: 40px;"}
        [:i.bi.text-success-emphasis.fs-5 {:class (:icon subgrid)}]]
       [:div
        [:div.d-flex.align-items-center.gap-2
         [:h6.mb-0.fw-semibold (:title subgrid)]
         [:span.badge.rounded-pill.bg-success-subtle.text-success-emphasis.fs-7.fw-semibold "1:N"]]
        [:small.text-muted (str (or (:count subgrid) 0) " " (i18n/tr :tabgrid/total-records))]]]
      (when (:new actions)
        [:a.btn.btn-sm.btn-success.px-3.fw-semibold.text-white.shadow-sm
         {:href (str "/admin/" sg-name "/add-form/" parent-id
                     "?return_url=" (ring-codec/url-encode return-url)
                     "&parent_entity=" entity-name)}
         [:i.bi.bi-plus.me-1] (i18n/tr :common/new)])]
     [:div.card-body.p-0
      (when (seq (:records subgrid))
        (subgrid-search-bar (str entity-name "-" sg-name)))
      (if (seq (:records subgrid))
        [:div.table-responsive
         [:table.table.table-hover.mb-0.tg-sg-table
          {:data-sg-key (str entity-name "-" sg-name)}
          [:thead.bg-body-tertiary
           [:tr
            (for [[fid label] sg-fields]
              [:th.fw-semibold.text-uppercase.fs-7.text-muted.px-3 label])
            [:th.text-end.fw-semibold.text-uppercase.fs-7.text-muted.px-3 (i18n/tr :common/actions)]]]
          [:tbody
           (for [record (:records subgrid)
                 :let [rid (:id record)]]
             ^{:key rid}
             [:tr
              (for [[fid label] sg-fields]
                [:td.px-3.py-2 {:data-label label} (field-value (get record fid))])
              [:td.text-end.text-nowrap.px-3.py-2 {:data-label (i18n/tr :common/actions)}
               (action-btns record)]])]]]
        [:div.text-center.py-5.tg-empty-state
         [:div.d-inline-flex.align-items-center.justify-content-center.rounded-3.bg-success-subtle.mb-3
          {:style "width: 64px; height: 64px;"}
          [:i.bi.bi-inbox.fs-1.text-success-emphasis]]
         [:h6.fw-semibold.text-muted.mb-1 (i18n/tr :grid/no-records)]
         (when (:new actions)
           [:a.btn.btn-sm.btn-outline-success.px-3.fw-semibold.mt-2
            {:href (str "/admin/" sg-name "/add-form/" parent-id
                        "?return_url=" (ring-codec/url-encode return-url)
                        "&parent_entity=" entity-name)}
            [:i.bi.bi-plus-circle.me-1] (i18n/tr :common/new)])])]]))

(defn- render-subgrid-card
  [entity-name parent-id subgrid]
  (case (:relationship-type subgrid)
    :many-to-many (render-m2m-card entity-name parent-id subgrid)
    :one-to-one   (render-1to1-card entity-name parent-id subgrid)
    (render-1toN-card entity-name parent-id subgrid)))

(defn render-m2m-pane [request entity-name entity-title subgrid parent-id]
  (render-subgrid-card entity-name parent-id subgrid))

(defn render-tabgrid [request entity-name title fields rows all-rows actions subgrids]
  (let [selected-id (or (some-> (get-in request [:params :id]) str)
                        (when-let [row (first rows)]
                          (str (get-record-id entity-name row))))
        selected-row (some #(when (= (str (get-record-id entity-name %)) selected-id) %) rows)
        search-id    (str "record-list-" entity-name "-search")]
    [:div.tabgrid-container
     ;; ── Scroll selected into view ──
     [:script "(function(){
       var el=document.querySelector('.list-group-item.active');
       if(el){var c=el.closest('.tg-list-scroll');
       if(c){var top=el.offsetTop-c.offsetTop-c.clientHeight/2+el.clientHeight/2;
       c.scrollTop=Math.max(0,top);}}
     })()"]
     ;; ── Breadcrumb ──
     [:div.mb-2
      [:nav.breadcrumb-nav
       [:ol.breadcrumb.mb-0
        [:li.breadcrumb-item
         [:a.text-decoration-none {:href "/"} (i18n/tr :breadcrumb/home)]]
        [:li.breadcrumb-item.active {:aria-current "page"} title]]]]
     ;; ── Entity heading ──
     [:div.d-flex.align-items-center.gap-2.mb-3
      [:h3.fw-bold.mb-0.ls-tight title]
      [:span.text-muted.fs-7 (str "(" (count all-rows) ")")]]
     ;; ── Main layout ──
     [:div.row.g-4
      [:div.col-12.col-xl-4
       (render-record-list entity-name fields all-rows selected-id actions)]
      [:div.col-12.col-xl-8.tg-detail-col
       (if selected-row
         [:div
          (render-entity-summary entity-name title fields selected-row actions)
          (when (seq subgrids)
            [:div
             (for [subgrid subgrids]
               ^{:key (str entity-name "-" (name (:entity subgrid)))}
               (render-subgrid-card entity-name selected-id subgrid))])]
         ;; ── Empty state: no record selected ──
         [:div.card.shadow-sm.border-0.overflow-hidden
          [:div.card-body.text-center.py-5.tg-empty-state
           [:div.d-inline-flex.align-items-center.justify-content-center.rounded-3.bg-body-tertiary.mb-3
            {:style "width: 80px; height: 80px;"}
            [:i.bi.bi-cursor.text-body-tertiary {:style "font-size: 2rem;"}]]
           [:h5.fw-semibold.text-muted (i18n/tr :tabgrid/no-record-selected)]
           [:p.text-muted.mb-0 (i18n/tr :tabgrid/select-hint)]]])]]
     ;; ── JS: Ctrl+K, keyboard nav, mobile auto-scroll ──
     (tabgrid-js entity-name search-id)]))
