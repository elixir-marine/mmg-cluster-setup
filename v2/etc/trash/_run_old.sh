
sudo su hdfs

NUM_WORKERS=5
METAPIPE_DIR=/data/metapipe
DEPENDENCIES_PATH=$METAPIPE_DIR/metapipe/tools/blast-2.2.19/bin:$METAPIPE_DIR/metapipe/tools/interproscan-5.18-57.0:$METAPIPE_DIR/metapipe/bin

export PATH=$DEPENDENCIES_PATH:$PATH
export HADOOP_USER_NAME=hdfs

export HADOOP_CONF_DIR=/etc/hadoop/conf
export YARN_CONF_DIR=/etc/hadoop/conf
export SPARK_YARN_USER_ENV="PATH=$PATH"

java -jar $METAPIPE_DIR/workflow-assembly-0.1-SNAPSHOT.jar validate

sleep 2

spark-submit --master yarn --deploy-mode client --num-executors $NUM_WORKERS $METAPIPE_DIR/workflow-assembly-0.1-SNAPSHOT.jar execution-manager --executor-id test-executor --num-partitions 1000 --config-file $METAPIPE_DIR/.metapipe/conf.json --job-tags csc-test 2>&1
# spark-submit --master yarn --deploy-mode client $METAPIPE_DIR/workflow-assembly-0.1-SNAPSHOT.jar execution-manager --executor-id test-executor --num-partitions 1000 --config-file $METAPIPE_DIR/.metapipe/conf.json --job-tags csc-test

# 4 future service implementation?
#sudo systemctl daemon-reload
#while :
#do
#    case "$1" in
#        "start") sudo systemctl enable /etc/systemd/system/metapipe.service
#                 sudo systemctl start metapipe.service
#                 ;;
#        "stop") sudo systemctl stop metapipe.service
#                ;;
#        "restart") sudo systemctl restart metapipe.service
#                   ;;
#        -*) usage "bad argument $1";;
#        *) break;;
#    esac
#    shift
#done