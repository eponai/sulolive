#!/bin/bash

lein prod-build
react-native run-ios --configuration "Release" --no-packager
