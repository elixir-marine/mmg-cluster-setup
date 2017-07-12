

source _init.sh

echo "LAUNCHING META-PIPE FUNCTIONAL ANALYSIS..."

source $SPARK_HOME/sbin/start-all.sh

#SPARK_CONF="\
#    --driver-cores $CORES_MASTER \
#    --driver-memory $(($RAM_MASTER))G \
#    --executor-cores $CORES_PER_EXECUTOR \
#    --executor-memory $(($RAM_PER_EXECUTOR))G \
#    --conf spark.task.cpus=$CORES_PER_EXECUTOR \
#    --conf spark.cores.max=$(($CORES_PER_EXECUTOR * $NUM_WORKERS))"

SPARK_CONF="\
    --driver-memory $(($RAM_MASTER))G \
    --executor-memory $(($RAM_PER_EXECUTOR))G \
    --conf spark.task.cpus=$CORES_PER_EXECUTOR \
    --conf spark.cores.max=$(($CORES_PER_SLAVE * $NUM_SLAVES))"

# Spark Standalone, client mode
$SPARK_HOME/bin/spark-submit \
    --master spark://$(hostname):7077 \
    $SPARK_CONF \
    $SW_EXECUTABLE \
        execution-manager --executor-id test-executor --num-partitions $NUM_PARTITIONS --num-concurrent-jobs -1 \
        --config-file .metapipe/conf.json --job-tags $SPARK_JOB_TAG

#source $SPARK_HOME/sbin/stop-all.sh





