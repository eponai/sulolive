#!/bin/sh
### BEGIN INIT INFO
# chkconfig: 2345 85 85
# description: Red5 Pro streaming server
# Provides:          Red5 Pro
# Required-Start:    $local_fs $network
# Required-Stop:     $local_fs
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Red5Pro
# processname: red5
### END INIT INFO

PROG=red5
RED5_HOME=/usr/local/red5pro
DAEMON=$RED5_HOME/$PROG.sh
PIDFILE=/var/run/$PROG.pid

start() {
  # check to see if the server is already running
  if netstat -an | grep ':5080'; then
    echo "Red5 is already started..."
    while netstat -an | grep ':5080'; do
      # wait 5 seconds and test again
      sleep 5
    done
  fi
  cd ${RED5_HOME} && ./red5.sh &
}

stop() {
  cd ${RED5_HOME} && ./red5-shutdown.sh
}

case "$1" in
  start)
    start
    exit 1
  ;;
  stop)
    stop
    exit 1
  ;;
  restart)
    stop
    start
    exit 1
  ;;
  **)
    echo "Usage: $0 {start|stop|restart}" 1>&2
    exit 1
  ;;

esac
