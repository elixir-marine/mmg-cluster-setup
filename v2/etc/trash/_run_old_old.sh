#sudo cp _run.sh ~

sudo su hdfs
cd ~

NUM_NODES=3
METAPIPE_DIR=/data/metapipe
DEPENDENCIES_PATH=$METAPIPE_DIR/metapipe/tools/blast-2.2.19/bin:$METAPIPE_DIR/metapipe/tools/interproscan-5.18-57.0:$METAPIPE_DIR/metapipe/bin

export PATH=$DEPENDENCIES_PATH:$PATH
export HADOOP_USER_NAME=hdfs

#echo "export HADOOP_USER_NAME=hdfs" | sudo tee --append /etc/spark/conf/spark-env.sh
#echo "export PATH=$DEPENDENCIES_PATH:\$PATH" | sudo tee --append /etc/spark/conf/spark-env.sh
#echo 'export SPARK_EXECUTOR_INSTANCES="2"' | sudo tee --append /etc/spark/conf/spark-env.sh
#echo 'export SPARK_EXECUTOR_CORES="16"' | sudo tee --append /etc/spark/conf/spark-env.sh
#echo 'export SPARK_EXECUTOR_MEMORY="10G"' | sudo tee --append /etc/spark/conf/spark-env.sh
#echo 'export SPARK_DRIVER_MEMORY="10G"' | sudo tee --append /etc/spark/conf/spark-env.sh
#echo 'export SPARK_TASK_CPUS="32"' | sudo tee --append /etc/spark/conf/spark-env.sh

export HADOOP_CONF_DIR=/etc/hadoop/conf
export YARN_CONF_DIR=/etc/hadoop/conf
export SPARK_YARN_USER_ENV="PATH=$PATH"
##export SPARK_EXECUTOR_INSTANCES="2"
#export SPARK_EXECUTOR_CORES="16"
#export SPARK_TASK_CPUS=$(($NUM_NODES * $SPARK_EXECUTOR_CORES))
##export SPARK_EXECUTOR_MEMORY="77G"
##export SPARK_DRIVER_MEMORY="57G"
#export SPARK_EXECUTOR_MEMORY="60G"
#export SPARK_DRIVER_MEMORY="55G"
#export SPARK_YARN_AM_MEMORY="10G"

java -jar $METAPIPE_DIR/workflow-assembly-0.1-SNAPSHOT.jar validate

sleep 2

spark-submit --master yarn --num-executors $NUM_NODES $METAPIPE_DIR/workflow-assembly-0.1-SNAPSHOT.jar execution-manager --executor-id test-executor --num-partitions 1000 --config-file $METAPIPE_DIR/.metapipe/conf.json --job-tags csc-test 2>&1
# spark-submit --master yarn $METAPIPE_DIR/workflow-assembly-0.1-SNAPSHOT.jar execution-manager --executor-id test-executor --num-partitions 1000 --config-file $METAPIPE_DIR/.metapipe/conf.json --job-tags csc-test

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