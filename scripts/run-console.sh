#!/bin/bash -eu
url=

if [ "$1" != "" ]; then
    case $1 in
        -r | --remote )         if [ "$3" != "" -a "$4" != "" ] ; then
					url="datomic:ddb://us-east-1/eponai-jourmoney-2?aws_access_key_id=$3&aws_secret_key=$4"
				else
					echo "must provide aws_access_key_id and aws_secret_key with remote option"
					exit
				fi
				;;
        -l | --local )    	url="datomic:dev://localhost:4334/"
                                ;;
        -h | --help )           echo "Usage:
[-r -l] alias [aws_access_key_id aws_secret_key]

Arguments
-----------------------
alias  	any string for identifying the storage in the console UI.

Options
------------------------
-l	local storage, using datomic:dev://localhost:4334/
-r 	remote storage, using AWS storage. 
	Requires an aws_access_key_id and aws_secret_key in that order.
		aws_access_key_id       Access key for the AWS user.
		aws_secret_key          Secret token for the AWS user."
                                exit
    esac
fi

if [ "$url" != "" ] ; then
	echo "Connecting to transactor: "$2" $url"
	$DATOMIC_HOME/bin/console -p 8080 "$2" "$url"
fi

