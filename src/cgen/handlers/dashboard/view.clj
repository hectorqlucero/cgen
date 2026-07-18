(ns cgen.handlers.dashboard.view)

(defn- card
  [title count]
  [:div.col-12.col-sm-6.col-md-4.mb-2
   [:div.card
    [:div.card-body
     [:h5.card-title.text-primary title]
     [:p.card-text.fw-bolder count]]]])

(defn main
  [title stats]
  [:div.container-fluid.text-center.text-capitalize.bg-primary
   {:style "max-width: min(600px, 100%);"}
   [:h1 title]
   [:div.row
    (map (fn [[k v]] (card (name k) v)) stats)]])
