#!/bin/bash -eu

script_dir=$(dirname $0)

# 1. Update re-natal version
"$script_dir"/upgrade-re-natal.sh

# 2. Run the re-natal upgrade command to see if there are any changes we want to keep

re-natal upgrade

# 3. Prompt developer what to do with these changes

echo ";;;;; Almost there... -----------------"
echo "Re-natal has upgraded our project files."
echo "Follow these instructions to complete the upgrade"
echo ""
echo ";;;;; Action: -------------------------"
echo "Check the files if there are any changes"
echo " we want to keep."
echo ""
echo ";;;;; Verify: -------------------------"
echo "git checkout the files you want to ignore changes to"
echo " and git commit the changes you want to keep."
echo ""
echo ";;;;; Action: -------------------------"
echo "Update react native using their own docs:"
echo "https://facebook.github.io/react-native/docs/upgrading.html"
echo "Note: Instead of running 'react-native upgrade'"
echo " run: 'node node_modules/react-native/local-cli/cli.js upgrade'"
echo " after you've updated react-native version."
echo ""
echo "And you're done"
