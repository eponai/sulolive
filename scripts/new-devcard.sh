#!/bin/bash -eu

script_dir=$(dirname $0)
proj_dir="$script_dir/.."

# pass the new clojure namespace
ns=$1
ns_path=$(echo "$ns" | tr . /)
devcard_suffix="_dc"

# paths
src_path="$proj_dir/src/client/$ns_path.cljs"
tst_path="$proj_dir/test/devcards/$ns_path$devcard_suffix.cljs"

mkdir -p $(dirname "$src_path")
mkdir -p $(dirname "$tst_path")

# output src path
echo "(ns $ns
  (:require [om.next :as om :refer-macros [defui]]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]))" > "$src_path"

# output test path
echo "(ns $ns$devcard_suffix
  (:require [devcards.core :as dc]
            [$ns :as n]
            [sablono.core :refer-macros [html]])
  (:require-macros [devcards.core :refer [defcard]]))

(defcard generated-card
  (html [:div \"generated. remove me from $ns\"]))" > "$tst_path"

echo "Now add the following line to devcards main"
echo "[$ns$devcard_suffix]"

