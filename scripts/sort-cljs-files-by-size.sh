#!/bin/bash

find resources/public/dev/js/out/eponai -type f -exec wc -c \{\} \; | sort
