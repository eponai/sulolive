#!/bin/bash -eux

## Step 0:
## EC2 Instance Setup (https://www.red5pro.com/docs/server/awsinstall/):
## In the AWS Console, Select EC2 Dashboard
## Click Launch Instance to create a new EC2 instance
## Select the latest Ubuntu Server from the list (Note: we suggest that you choose ubuntu over the Amazon Linux AMI because the latter does not support all of the libraries needed for some Red5 Pro functionality).

## Step 1, run script with input parameters.
## Step 2. Watch the output, as everything is setup and red5pro is started.
## Step 3. Terminate the script when everything is setup.

### Inputs

ec2_host="$1"
pem_key="$2"
red5_server_release_path="$3"

### Setup

script_dir="$(dirname $0)"
initd_script="$script_dir/red5pro-initd-service.sh"
deps_script="$script_dir/install-red5-deps.sh"

ec2_host_with_login="ubuntu@$ec2_host"
remote_script_dir="/home/ubuntu"
remote_initd_script=$remote_script_dir/$(basename "$initd_script")
remote_deps_script=$remote_script_dir/$(basename "$deps_script")

### red5

red5_install_parent_dir="/usr/local"
red5_filename=$(basename "$red5_server_release_path")
red5_unzip_name=$(basename "$red5_filename" .zip)
red5_tmp_path="/tmp/$red5_filename"
red5_install_dir="$red5_install_parent_dir/red5pro"

### Program

scp -i "$pem_key" "$red5_server_release_path" "$ec2_host_with_login":"$red5_tmp_path"
scp -i "$pem_key" "$initd_script" "$ec2_host_with_login":"$remote_initd_script"
scp -i "$pem_key" "$deps_script" "$ec2_host_with_login":"$remote_deps_script"

## Multiple -t flags is different than one. See manual
ssh -t -t -i "$pem_key" "$ec2_host_with_login" << EOF
  ## Sudo it up
  sudo su

  ## Install deps
  chmod u+x "$remote_deps_script" 
  "$remote_deps_script"

  ## Install red5pro
  cp "$red5_tmp_path" "$red5_install_parent_dir"
  cd "$red5_install_parent_dir" 
  unzip "$red5_filename"
  rm -rf "$red5_install_dir"
  mv "$red5_unzip_name" "$red5_install_dir"
   
  ## Define red5pro as a service
  cp "$remote_initd_script" /etc/init.d/red5pro 
  chmod u+x /etc/init.d/red5pro 
  
  ## Make red5pro start on boot/reboot
  /usr/sbin/update-rc.d red5pro defaults
  /usr/sbin/update-rc.d red5pro enable

  ## Start red5pro
  /etc/init.d/red5pro start
EOF
  
