(ns cgen.tabgrid.render
  "Entity Workspace -- navigator + pinned record header + relationship tabs."
  (:require
   [clojure.string :as str]
   [cgen.i18n.core :as i18n]
   [hiccup.util :refer [raw-string]]
   [cgen.engine.config :as config]
   [cgen.web.csrf :refer [csrf-field]]))

;;; -- Utilities -------------------------------------------------------

(defn- safe-id [s]
  (-> (str s) str/lower-case (str/replace #"[^a-z0-9]+" "-")))

(defn get-record-id
  "Returns the primary key; composite PKs are pipe-joined."
  [entity-name row]
  (try
    (let [pk (:primary-key (config/get-entity-config entity-name))]
      (if (and pk (vector? pk) (> (count pk) 1))
        (str/join "|" (map #(get row %) pk))
        (:id row)))
    (catch Exception _ (:id row))))

(defn parent-display-label
  "First non-id string value, falling back to \"#<id>\"."
  [fields parent-row selected-parent-id]
  (or (some (fn [[fid _]]
              (when (not= fid :id)
                (let [v (get parent-row fid)]
                  (when (and v (string? v) (seq v)) v))))
            fields)
      (str "#" selected-parent-id)))

(defn- render-field-value [value]
  (if (and (string? value) (re-find #"^<" value))
    (raw-string value)
    value))

(defn- row-display-label [fields row]
  (or (some (fn [[fid _]]
              (when (not= fid :id)
                (let [v (get row fid)]
                  (when (and v (string? v) (seq v)) v))))
            fields)
      (str "#" (:id row))))

(defn- row-secondary-label [fields row]
  (second
   (keep (fn [[fid _]]
           (when (not= fid :id)
             (let [v (get row fid)]
               (when (and v (string? v) (seq v)) v))))
         fields)))

(defn- ws-initials [s]
  (let [s (str s)]
    (str/upper-case (subs s 0 (min 2 (count s))))))

;;; -- Navigator (left panel) ------------------------------------------

(defn render-navigator
  [request entity-name title fields all-rows selected-parent-id actions]
  [:aside.ws-nav
   [:div.ws-nav-header
    [:div.ws-nav-title
     [:span title]
     [:span.ws-count-badge (count all-rows)]]
    (when (:new actions)
      [:a.ws-new-btn
       {:href (str "/admin/" entity-name "/add-form")
        :title (str (i18n/tr request :common/new) " " title)}
       [:i.bi.bi-plus-lg]])]
   [:div.ws-nav-search
    [:div.ws-search-wrap
     [:i.bi.bi-search.ws-search-icon]
     [:input.ws-search-input
      {:type "search" :disabled "disabled"
       :placeholder (str (i18n/tr request :common/search) "...")}]]]
   [:ul.ws-record-list
    {:id (str entity-name "-record-list")}
    (for [row    all-rows
          :let   [row-id    (str (get-record-id entity-name row))
                  label     (row-display-label fields row)
                  secondary (row-secondary-label fields row)
                  active?   (= row-id (str selected-parent-id))]]
      [:li.ws-record-item
       {:class (when active? "active")}
       [:a.ws-record-link
        {:href (str "/admin/" entity-name "/" row-id)
         :title (str label (when secondary (str " — " secondary)))}
        [:span.ws-avatar (ws-initials label)]
        [:div.ws-record-info
         [:span.ws-record-label label]
         (when secondary [:span.ws-record-secondary secondary])]]])]])

;;; -- Pinned record header --------------------------------------------

(defn- confirm-js
  "JS confirm() string for delete onsubmit."
  [request]
  (str "return confirm('" (i18n/tr request :confirm/delete) "')"))

(defn- field-display-value
  "Resolved display value — runs compute-fn for computed fields."
  [entity-name field-id row]
  (let [cfg (first (filter #(= (:id %) field-id)
                           (:fields (config/get-entity-config entity-name))))]
    (if (= :computed (:type cfg))
      (some-> (:compute-fn cfg) (apply [row]))
      (get row field-id))))

(defn- render-field-pair
  "Single label/value field cell for the fields grid."
  [entity-name field-id field-label row]
  [:div.ws-field
   [:span.ws-field-label field-label]
   [:span.ws-field-value
    (let [v (render-field-value (field-display-value entity-name field-id row))]
      (if (or (nil? v) (= v ""))
        [:span.text-muted "—"]
        v))]])

(defn- render-record-header
  [request entity-name title fields row actions]
  (if-not row
    [:div.ws-empty-state [:i.bi.bi-inbox] [:p (i18n/tr request :grid/no-records)]]
    (let [label (parent-display-label fields row nil)
          rid   (get-record-id entity-name row)]
      [:div.ws-record-header
       [:div.ws-record-hero
        [:div.ws-hero-avatar (ws-initials label)]
        [:div.ws-hero-meta
         [:h2.ws-hero-name label]
         [:span.ws-hero-id [:i.bi.bi-hash] rid [:span.ms-2.fw-normal.text-muted title]]]
        [:div.ws-hero-actions
         (when (:edit actions)
           [:a.btn.btn-sm.btn-primary
            {:href (str "/admin/" entity-name "/edit-form/" rid)}
            [:i.bi.bi-pencil.me-1] (i18n/tr request :common/edit)])
         (when (:delete actions)
           [:form.d-inline
            {:method "POST"
             :action (str "/admin/" entity-name "/delete/" rid)
             :onsubmit (confirm-js request)}
            (csrf-field)
            [:button.btn.btn-sm.btn-outline-danger {:type "submit"}
             [:i.bi.bi-trash.me-1] (i18n/tr request :common/delete)]])]]
       [:div.ws-fields-grid
        (for [[field-id field-label] fields]
          (render-field-pair entity-name field-id field-label row))]])))

;;; -- 1:1 pane --------------------------------------------------------

(defn- render-o2o-pane
  [request parent-entity-name parent-id subgrid]
  (let [sg-name     (name (:entity subgrid))
        pane-id     (str parent-entity-name "-" sg-name "-pane")
        record      (:record subgrid)
        fields      (:fields subgrid)
        actions     (:actions subgrid)
        return-url  (str "/admin/" parent-entity-name "/" parent-id)]
    [:div.ws-o2o-card
     [:div.ws-o2o-header
      [:span.ws-o2o-title
       [:i.me-1 {:class (or (:icon subgrid) "bi bi-person-vcard")}]
       (:title subgrid)]
      [:div.d-flex.align-items-center.gap-2
       (if record
         [:span.ws-o2o-status-linked
          [:i.bi.bi-check-circle-fill.me-1] (i18n/tr request :subgrid/linked)]
         [:span.ws-o2o-status-unlinked
          [:i.bi.bi-dash-circle.me-1] (i18n/tr request :subgrid/not-set)])
       (if record
         (when (:edit actions)
           [:a.btn.btn-sm.btn-outline-primary
            {:href (str "/admin/" sg-name "/edit-form/" (:id record)
                        "?return_url=" return-url "&active_tab=" pane-id "&edited_id=" (:id record))}
            [:i.bi.bi-pencil.me-1] (i18n/tr request :common/edit)])
         [:a.btn.btn-sm.btn-outline-primary
          {:href (str "/admin/" sg-name "/add-form"
                      "?parent_id=" parent-id
                      "&parent_entity=" parent-entity-name
                      "&active_tab=" pane-id)}
          [:i.bi.bi-plus-circle.me-1]
          (i18n/tr request :subgrid/create {:title (:title subgrid)})])]]
     (if record
       [:div.ws-o2o-body
        (for [[fid flabel] fields
              :let [v (get record fid)]
              :when (some? v)]
          [:div.ws-o2o-field
           [:span.ws-o2o-field-label flabel]
           [:span.ws-o2o-field-value (render-field-value v)]])]
       [:div.ws-o2o-empty
        [:i.bi.bi-dash-circle.me-2]
        (i18n/tr request :subgrid/no-linked-yet {:title (:title subgrid)})])]))

;;; -- 1:M pane --------------------------------------------------------

(defn- render-subgrid-table
  "Server-rendered 1:M subgrid table."
  [request parent-entity parent-id sg-name fields records actions]
  (let [pane-id (str parent-entity "-" sg-name "-pane")
        return-url (str "/admin/" parent-entity "/" parent-id)
        show-edit? (:edit actions)
        show-delete? (:delete actions)]
    (if (seq records)
      (into [:table.table.table-hover.table-bordered.table-sm.subgrid-table
             {:id (str parent-entity "-" sg-name "-table")}
             [:thead
              [:tr
               (for [[_ label] fields] [:th.subgrid-sortable label [:i.bi.bi-chevron-expand.ms-1]])
               (when (or show-edit? show-delete?)
                 [:th {:style "width:120px"} (i18n/tr request :common/actions)])]]]
            [(into [:tbody]
                   (for [row records]
                     (into [:tr {:data-row-id (:id row)}]
                           (concat
                            (for [[fid _] fields]
                              [:td.small {:title (some-> (get row fid) str)}
                               (render-field-value (get row fid))])
                            (when (or show-edit? show-delete?)
                              [[:td
                                [:div.d-flex.gap-1
                                 (when show-edit?
                                   [:a.btn.btn-sm.btn-outline-primary
                                    {:href (str "/admin/" sg-name "/edit-form/" (:id row)
                                                "?return_url=" return-url "&active_tab=" pane-id "&edited_id=" (:id row))}
                                    [:i.bi.bi-pencil]])
                                 (when show-delete?
                                   [:form.d-inline
                                    {:method "POST"
                                     :action (str "/admin/" sg-name "/delete/" (:id row))}
                                    (csrf-field)
                                    [:input {:type "hidden" :name "return_url" :value return-url}]
                                    [:input {:type "hidden" :name "active_tab" :value pane-id}]
                                    [:button.btn.btn-sm.btn-outline-danger
                                     {:type "submit"
                                      :onclick (str "return confirm('" (i18n/tr request :confirm/delete) "')")}
                                     [:i.bi.bi-trash]]])]]])))))])
      [:div.text-center.p-4.text-muted
       [:i.bi.bi-inbox {:style "font-size:1.5rem"}]
       [:p.mt-2 (i18n/tr request :grid/no-records)]])))

(defn- render-otm-pane
  [request entity-name subgrid selected-parent-id]
  (let [sg-name (safe-id (name (:entity subgrid)))
        sg-entity-name (name (:entity subgrid))
        records (:records subgrid)
        fields (:fields subgrid)
        actions (:actions subgrid)]
    [:div.ws-pane-card
     [:div.ws-pane-toolbar
      [:span.ws-pane-title
       [:i.me-1 {:class (or (:icon subgrid) "bi bi-list-ul")}]
       (:title subgrid)
       [:span.badge.bg-secondary.ms-2 (or (:count subgrid) 0)]]
      [:a.btn.btn-sm.btn-success
       {:href (str "/admin/" sg-entity-name "/add-form"
                   "?parent_id=" selected-parent-id
                   "&parent_entity=" entity-name
                   "&active_tab=" entity-name "-" sg-name "-pane")}
       [:i.bi.bi-plus-circle.me-1] (i18n/tr request :common/new)]]
     (when (seq records)
       [:div.subgrid-search-wrap
        [:input.subgrid-search.form-control.form-control-sm
         {:type "search" :placeholder (str (i18n/tr request :common/search) "...")}]
        [:span.badge.rounded-pill.bg-light.text-secondary.border.subgrid-clear
         [:i.bi.bi-x] (str " " (i18n/tr request :common/clear))]])
     (render-subgrid-table request entity-name selected-parent-id
                           sg-name fields records actions)]))

(defn- render-m2m-row
  [request fields has-pivot? through fk related-fk parent-id entity-name pane-id row]
  (let [return-url (str "/admin/" entity-name "/" parent-id)
        related-id (or (get row (keyword related-fk)) (:id row))]
    [:tr {:data-row-id related-id}
     (for [[fid _] fields]
       [:td.small {:title (some-> (get row fid) str)}
        (render-field-value (get row fid))])
     [:td
      [:div.d-flex.gap-1
       (when has-pivot?
         [:a.btn.btn-sm.btn-outline-primary
          {:href (str "/tabgrid/pivot-form"
                      "?through_table=" through
                      "&parent_fk=" fk
                      "&parent_id=" parent-id
                      "&related_fk=" related-fk
                      "&related_id=" related-id
                      "&active_tab=" pane-id
                      "&return_url=" return-url)
           :title (i18n/tr request :pivot/edit-attributes)}
          [:i.bi.bi-sliders]])
       [:form.d-inline
        {:method "POST" :action "/tabgrid/dissociate"}
        (csrf-field)
        [:input {:type "hidden" :name "through_table" :value through}]
        [:input {:type "hidden" :name "parent_fk" :value fk}]
        [:input {:type "hidden" :name "parent_id" :value parent-id}]
        [:input {:type "hidden" :name "related_fk" :value related-fk}]
        [:input {:type "hidden" :name "related_id" :value related-id}]
        [:input {:type "hidden" :name "active_tab" :value pane-id}]
        [:button.btn.btn-sm.btn-outline-danger
         {:type "submit"
          :title (i18n/tr request :subgrid/unlink)
          :onclick (str "return confirm('" (i18n/tr request :confirm/unlink) "')")}
         [:i.bi.bi-x-circle-fill]]]]]]))

(defn render-m2m-pane
  [request entity-name entity-title parent-display subgrid selected-parent-id]
  (let [sg-name (safe-id (name (:entity subgrid)))
        records (:records subgrid)
        fields (:fields subgrid)
        through (name (:through-table subgrid))
        fk (name (:foreign-key subgrid))
        related-fk (name (:related-fk subgrid))
        pane-id (str entity-name "-" sg-name "-pane")
        junction-entity (keyword (or through (name (:entity subgrid))))
        junction-cfg (try (config/get-entity-config junction-entity)
                          (catch Exception _ nil))
        fk-ids #{(keyword fk) (keyword related-fk)}
        has-pivot? (boolean (some #(and (not= :hidden (:type %))
                                        (not (contains? fk-ids (:id %))))
                                  (:fields junction-cfg)))
        link-url (str "/tabgrid/link-form"
                      "?through_table=" through
                      "&parent_fk=" fk
                      "&parent_id=" selected-parent-id
                      "&related_entity=" (name (:related-entity subgrid))
                      "&related_fk=" related-fk
                      "&return_url=/admin/" entity-name "/" selected-parent-id
                      "&active_tab=" pane-id)]
    [:div.ws-pane-card
     [:div.ws-pane-toolbar
      [:span.ws-pane-title
       [:i.me-1 {:class (or (:icon subgrid) "bi bi-link-45deg")}]
       (or entity-title (:title subgrid))
       [:span.badge.bg-secondary.ms-2 (or (:count subgrid) 0)]]
      [:a.btn.btn-sm.btn-success
       {:href link-url}
       [:i.bi.bi-plus-circle.me-1] (i18n/tr request :subgrid/link)]]
     (when (seq records)
       [:div.subgrid-search-wrap
        [:input.subgrid-search.form-control.form-control-sm
         {:type "search" :placeholder (str (i18n/tr request :common/search) "...")}]
        [:span.badge.rounded-pill.bg-light.text-secondary.border.subgrid-clear
         [:i.bi.bi-x] (str " " (i18n/tr request :common/clear))]])
     (if (seq records)
       (into [:table.table.table-hover.table-sm.table-bordered.mb-0.subgrid-table
              {:id (str entity-name "-" sg-name "-table")}
              [:thead
               [:tr
                (for [[_ label] fields] [:th.subgrid-sortable label [:i.bi.bi-chevron-expand.ms-1]])
                [:th {:style "width:100px"}
                 (i18n/tr request :common/actions)]]]]
             [(into [:tbody]
                    (map (partial render-m2m-row
                                  request fields has-pivot? through
                                  fk related-fk selected-parent-id entity-name pane-id)
                         records))])
       [:div.text-center.p-4.text-muted
        [:i.bi.bi-link {:style "font-size:2rem"}]
        [:p.mt-2 (i18n/tr request :subgrid/no-associations)]])]))

;;; -- Tab strip + panes -----------------------------------------------

(defn- tab-cls [rel-type idx]
  (str (case rel-type
         :one-to-one   "ws-tab ws-tab-o2o"
         :many-to-many "ws-tab ws-tab-m2m"
         "ws-tab ws-tab-otm")
       (when (= idx 0) " active")))

(defn- render-tab-strip [request entity-name subgrids]
  (into [:div.ws-tab-strip {:role "tablist"}]
        (map-indexed
         (fn [idx sg]
           (let [sg-name (safe-id (name (:entity sg)))
                 pane-id (str entity-name "-" sg-name "-pane")
                 rel-type (:relationship-type sg)
                 rel-label (case rel-type
                             :one-to-one (i18n/tr request :subgrid/rel-11)
                             :one-to-many (i18n/tr request :subgrid/rel-1n)
                             :many-to-many (i18n/tr request :subgrid/rel-nm))
                 cnt (:count sg)]
             [:button
              {:class (tab-cls rel-type idx)
               :role "tab"
               :data-pane (str "#" pane-id)}
              [:span (:title sg)]
              [:span.ws-tab-end
               [:span.ws-tab-pill rel-label]
               (when-let [c cnt]
                 [:span.ws-tab-pill c])]]))
         subgrids)))

(defn- render-tab-panes
  [request entity-name subgrids selected-parent-id]
  (into [:div.ws-tab-content]
        (map-indexed
         (fn [idx sg]
           (let [sg-name  (safe-id (name (:entity sg)))
                 pane-id  (str entity-name "-" sg-name "-pane")
                 rel-type (:relationship-type sg)]
             [:div
              {:id    pane-id
               :class (str "ws-tab-pane" (when (= idx 0) " active"))}
              (case rel-type
                :one-to-one
                (render-o2o-pane request entity-name selected-parent-id sg)
                :many-to-many
                (render-m2m-pane request entity-name nil nil sg selected-parent-id)
                (render-otm-pane request entity-name sg selected-parent-id))]))
         subgrids)))

;;; -- Tab switching JS (minimal, inline) ------------------------------

(defn- tab-switch-js
  "Inline vanilla JS for tab switching"
  []
  [:script
   "(function(){
      var s=document.querySelector('.ws-tab-strip');
      if(!s)return;
      s.addEventListener('click',function(e){
        var t=e.target.closest('.ws-tab');
        if(!t)return;
        var p=t.getAttribute('data-pane');
        if(!p)return;
        s.querySelectorAll('.ws-tab').forEach(function(x){x.classList.remove('active');});
        t.classList.add('active');
        document.querySelectorAll('.ws-tab-pane').forEach(function(x){x.classList.remove('active');});
        var pane=document.querySelector(p);
        if(pane)pane.classList.add('active');
      });

       var p=new URLSearchParams(window.location.search).get('active_tab');
       if(p){
         var b=document.querySelector('[data-pane=\"#'+p+'\"]');
         if(b)b.click();
         setTimeout(function(){
           var l=document.querySelector('.ws-record-list'),
               a=l&&l.querySelector('.ws-record-item.active');
           if(a)l.scrollTop=Math.max(0,a.offsetTop-l.clientHeight/2);
         },0);
       }
       var ei=new URLSearchParams(window.location.search).get('edited_id');
       if(ei){
         (function eiPoll(){
           var r=document.querySelector('[data-row-id=\"'+ei+'\"]');
           if(r){
             r.style.background='#c7d2fe';
             setTimeout(function(){r.scrollIntoView({block:'center',inline:'nearest'});},50);
           }else{setTimeout(eiPoll,50);}
         })();
       }
       var si=document.querySelectorAll('.subgrid-search');
       [].forEach.call(si,function(i){
         var sk='sg_'+i.closest('.ws-pane-card').querySelector('.subgrid-table').id;
         var sv=sessionStorage.getItem(sk);
         if(sv){i.value=sv;}
         i.addEventListener('input',function(){
           var q=this.value.toLowerCase().trim(),t=this.closest('.ws-pane-card').querySelector('.subgrid-table');
           if(!t)return;
           sessionStorage.setItem(sk,q);
           [].forEach.call(t.querySelectorAll('tbody tr'),function(r){
             var f=0; [].forEach.call(r.cells,function(c){if(c.textContent.toLowerCase().indexOf(q)>-1)f=1;});
             r.style.display=f?'':'none';
           });
         });
         if(sv){i.dispatchEvent(new Event('input',{bubbles:true}));}
         var ci=i.parentNode.querySelector('.subgrid-clear');
         if(ci){
           if(i.value)ci.style.display='inline';
           i.addEventListener('input',function(){ci.style.display=this.value?'inline':'none';});
           ci.addEventListener('click',function(){
             i.value='';ci.style.display='none';sessionStorage.setItem(sk,'');
             [].forEach.call(i.closest('.ws-pane-card').querySelector('.subgrid-table').querySelectorAll('tbody tr'),function(r){r.style.display='';});
           });
         }
       });
       [].forEach.call(document.querySelectorAll('.subgrid-table'),function(t){
         var hs=t.querySelectorAll('th.subgrid-sortable');
         [].forEach.call(hs,function(h){h.addEventListener('click',function(){
           var c=this.cellIndex,d=this.getAttribute('data-dir')||'asc';d=d==='asc'?'desc':'asc';
           this.setAttribute('data-dir',d);
           [].forEach.call(hs,function(x){x.querySelector('i').className='bi bi-chevron-expand ms-1';});
           this.querySelector('i').className=d==='asc'?'bi bi-chevron-up ms-1':'bi bi-chevron-down ms-1';
           var a=[].slice.call(t.querySelector('tbody').rows).sort(function(x,y){
             var u=x.cells[c].textContent.trim(),v=y.cells[c].textContent.trim();
             var p=parseFloat(u),q=parseFloat(v);
             return!isNaN(p)&&!isNaN(q)?(d==='asc'?p-q:q-p):d==='asc'?u.localeCompare(v):v.localeCompare(u);
           });
           a.forEach(function(r){t.querySelector('tbody').appendChild(r);});
         });});
       });
     })()"])

(defn render-accordion-content
  "Renders workspace tab content."
  [request entity-name title fields rows actions subgrids selected-parent-id]
  (let [parent-row (first rows)]
    [:div.ws-main
     (render-record-header request entity-name title fields parent-row actions)
     (when (seq subgrids)
       [:div.ws-tabs-container
        (render-tab-strip request entity-name subgrids)
        (render-tab-panes request entity-name subgrids selected-parent-id)])
     (tab-switch-js)]))

(defn render-tab-content
  "Legacy alias."
  [request entity-name title fields rows actions subgrids selected-parent-id]
  (render-accordion-content request entity-name title fields rows actions
                            subgrids selected-parent-id))

(defn render-parent-selector-modal
  "Modal to pick a parent record."
  [request entity-name title fields all-rows]
  [:div.modal.fade
   {:id (str entity-name "-select-parent-modal") :tabindex "-1"}
   [:div.modal-dialog.modal-xl
    [:div.modal-content
     [:div.modal-header.bg-primary.text-white
      [:h5.modal-title
       [:i.bi.bi-search.me-2] (i18n/tr request :common/select) " " title]
      [:button.btn-close.btn-close-white
       {:type "button" :data-bs-dismiss "modal"}]]
     [:div.modal-body
      [:table.table.table-hover.table-sm
       {:id (str entity-name "-select-table")}
       [:thead
        [:tr
         [:th (i18n/tr request :common/select)]
         (for [[_ label] fields] [:th label])]]
       [:tbody
        (for [row all-rows]
          [:tr
           [:td
            [:a.btn.btn-sm.btn-success
             {:href (str "/admin/" entity-name "/" (get-record-id entity-name row))}
             [:i.bi.bi-check-circle.me-1] (i18n/tr request :common/select)]]
           (for [[field-id _] fields]
             [:td (render-field-value (get row field-id))])])]]]
     [:div.modal-footer
      [:button.btn.btn-secondary {:type "button" :data-bs-dismiss "modal"}
       (i18n/tr request :common/close)]]]]])

(defn render-tabgrid
  "Entry point: full Entity Workspace."
  [request entity-name title fields rows all-rows actions subgrids]
  (let [first-row          (first rows)
        selected-parent-id (or (some-> (get-in request [:params :id]) str)
                               (when first-row
                                 (str (get-record-id entity-name first-row))))]
    [:div.tabgrid-container
     {:id                      (str entity-name "-tabgrid")
      :data-entity             entity-name
      :data-selected-parent-id (or selected-parent-id "")}
     [:div.ws-layout
      (render-navigator request entity-name title fields
                        all-rows selected-parent-id actions)
      (render-accordion-content request entity-name title fields rows actions subgrids selected-parent-id)]]))
