#!/bin/bash -eu

echo "Will compile an ios build which runs against your own"
echo " jourmoney server, i.e. lein repl, (start-server)"

export TIMBRE_LEVEL=":info"

lein clean
lein prod-build-ios-local

echo "############### MANUAL STEPS ##################"
echo "Clojurescript changes:"
echo "1. In env/prod/env/ios/local_main.cljs:"
echo "  - Put your computer's ip instead of the hardcoded one"
echo "React Native build steps:"
echo "1. Add more memory to the node bundle:"
echo "  - In node_modules/react-native/packager/react-native-xcode.sh:"
echo "  - Add --max_old_space_size=4092 after the call to \$NODE_BINARY"
echo "  - Why: Doing this because Xcodoe will call this script"
echo "  -      and we don't have enough memory by default."
echo "  - TODO: Script this or add this to an env variable somewhere."
echo "#######"
echo "Xcode steps:"
echo "1. In ios/JourMoneyApp/AppDelegate.m:"
echo '  - Uncomment jsCodeLocation = [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];'
echo "2. On the build drop down - between Stop and Device dropdown -"
echo "  - click on Edit Scheme..."
echo "   - Change Build Configuration to Release"
echo "3. Select your device and hit Run."
echo "############### END STEPS ######################"

