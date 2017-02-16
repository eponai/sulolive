(ns eponai.common.ui.icons
  (:require
    [clojure.string :as string]
    [eponai.common.ui.dom :as dom]
    [eponai.common.ui.elements.css :as css]))

(defn inline-html [code]
  {:pre [(every? string? code)]}
  (let [code (->> code
                  (map string/trim)
                  (apply str))]
    (dom/div {:dangerouslySetInnerHTML {:__html code}})))

(defn icon* [viewbox])
(defn video-camera []
  (dom/div
    (css/add-class ::css/icon)
    (inline-html
      ["<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<svg width=\"56px\" height=\"56px\" viewBox=\"0 0 56 56\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">"

       "<!-- Generator: Sketch 41.2 (35397) - http://www.bohemiancoding.com/sketch -->"
       "<title>Artboard</title>"
       "<desc>Created with Sketch.</desc>"
       "<defs>"
       "<rect id=\"path-1\" x=\"0\" y=\"0\" width=\"39\" height=\"30\" rx=\"2.51685393\"></rect>"
       "<mask id=\"mask-2\" maskContentUnits=\"userSpaceOnUse\" maskUnits=\"objectBoundingBox\" x=\"0\" y=\"0\" width=\"39\" height=\"30\" fill=\"white\">"
       "<use xlink:href=\"#path-1\"></use>"
       "</mask>"
       "<rect id=\"path-3\" x=\"38\" y=\"6\" width=\"6.46441948\" height=\"17.8277154\" rx=\"2.51685393\"></rect>"
       "<mask id=\"mask-4\" maskContentUnits=\"userSpaceOnUse\" maskUnits=\"objectBoundingBox\" x=\"0\" y=\"0\" width=\"6.46441948\" height=\"17.8277154\" fill=\"white\">"
       "<use xlink:href=\"#path-3\"></use>"
       "</mask>"
       "</defs>"
       "<g id=\"Page-1\" stroke=\"none\" stroke-width=\"1\" fill=\"none\" fill-rule=\"evenodd\">"
       "<g id=\"Artboard\" stroke=\"#000000\">"
       "<g id=\"Group\" transform=\"translate(1.000000, 13.000000)\">"
       "<use id=\"Rectangle\" mask=\"url(#mask-2)\" stroke-width=\"2\" xlink:href=\"#path-1\"></use>"
       "<use id=\"Rectangle-Copy\" mask=\"url(#mask-4)\" stroke-width=\"2\" xlink:href=\"#path-3\"></use>"
       "<path d=\"M44,21.9233913 L44,8.26798535 L53.3957434,5.02169873 C53.614434,4.94613979 53.7917178,5.06810503 53.7917178,5.30823266 L53.7917178,25.654906 C53.7917178,25.8887132 53.6217642,26.0061361 53.4078709,25.915376 L44,21.9233913 Z\" id=\"Triangle\"></path>"
       "</g>"
       "</g>"
       "</g>"
       "</svg>"])))

(defn shopping-bag []
  (dom/div
    (css/add-class ::css/icon)
    (inline-html
      ["<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<svg width=\"56px\" height=\"56px\" viewBox=\"0 0 56 56\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">\n    <!-- Generator: Sketch 41.2 (35397) - http://www.bohemiancoding.com/sketch -->\n    <title>Shopping</title>\n    <desc>Created with Sketch.</desc>\n    <defs></defs>\n    <g id=\"Page-1\" stroke=\"none\" stroke-width=\"1\" fill=\"none\" fill-rule=\"evenodd\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n        <g id=\"Shopping\" stroke=\"#000000\">\n            <path d=\"M18.1268372,21.7137275 L18.0717691,10.267609 C18.0717691,10.267609 21.771157,5.4176045 27.4469563,5.4176045 C33.1227557,5.4176045 36.8221436,10.539237 36.8221436,10.539237 L36.7463476,21.6103975 M20.069029,19.2905392 L34.9604116,19.2881495 M38.8846962,19.2294938 L43.2715801,19.2294938 L43.2715801,49.7386333 L11.8116225,49.7386333 L11.8116225,19.2294938 L16.1332497,19.2681632 M16.4254229,22.9682526 C16.4254229,22.9682526 18.1159023,25.1086251 19.8063817,23.0045322 M35.2268349,22.9818189 C35.2268349,22.9818189 36.9430207,25.1799392 38.6592066,22.9818189\" id=\"Path-3\"></path>\n        </g>\n    </g>\n</svg>"])))

(defn heart []
  (dom/div
    (css/add-class ::css/icon)
    (inline-html
      ["<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<svg width=\"56px\" height=\"56px\" viewBox=\"0 0 56 56\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">\n    <!-- Generator: Sketch 41.2 (35397) - http://www.bohemiancoding.com/sketch -->\n    <title>Heart</title>\n    <desc>Created with Sketch.</desc>\n    <defs></defs>\n    <g id=\"Page-1\" stroke=\"none\" stroke-width=\"1\" fill=\"none\" fill-rule=\"evenodd\">\n        <g id=\"Heart\" stroke=\"#000000\">\n            <path d=\"M27.4679804,13.2407505 C27.7341764,13.7137954 28.1501064,13.7004573 28.4235182,13.2167455 C28.4235182,13.2167455 31.8861104,5.34971159 41.5870762,6.18000258 C46.0856329,6.56502725 53.8745165,10.5780402 52.3469926,20.1892854 C50.8194688,29.8005307 28.7482165,49.0835778 28.7482165,49.0835778 C28.3349879,49.4506233 27.6580452,49.4517513 27.249383,49.0936732 C27.249383,49.0936732 5.10791898,30.1217378 3.5600441,20.1892854 C2.01216922,10.256833 9.70261999,6.60979185 14.1768573,6.18000258 C23.7231914,5.26299445 27.4679804,13.2407505 27.4679804,13.2407505 Z\" id=\"Path-2\"></path>\n        </g>\n    </g>\n</svg>"])))