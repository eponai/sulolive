#!/bin/bash -eu

script_dir=$(dirname $0)
cd "$script_dir/.."

# 1. Update re-natal

npm upgrade -g re-natal

# 2. Prepare .cljs files to be updated
# Note: These are the source files that
#       re-natal created the first time
#       it ran. We don't use these.

mkdir_and_touch() {
  mkdir -p "$(dirname $1)" && touch $1
}

mkdir_and_touch src/jour_money_app/ios/core.cljs
mkdir_and_touch src/jour_money_app/android/core.cljs

# 3. Upgrade our project files
re-natal upgrade 

# 4. Prompt developer what to do with these changes

echo ";;;;; Almost there... -----------------" 
echo "Re-natal has upgraded our project files."
echo "Follow these instructions to complete the upgrade"
echo ""
echo ";;;;; Action: -------------------------" 
echo "Check the files if there are any changes"
echo " which we want to keep."
echo ""
echo ";;;;; Action: -------------------------"
echo "package.json doesn't get updated. You may need to update it yourself."
echo "Go to: https://github.com/drapanjanas/re-natal"
echo "Check re-natal.coffee to see if there has been changes to the generated"
echo " package.json. Like 'rnVersion' or the 'scripts' key."
echo ""
echo ";;;;; Verify: -------------------------"
echo "git checkout the files you want to ignore changes to"
echo " and git commit the changes you want to keep."
echo "Then remove src/jour_money_app. Run:"
echo "rm -r src/jour_money_app"
echo "And you're done"

