

source _init.sh

echo "LAUNCHING META-PIPE FUNCTIONAL ANALYSIS..."

start-all.sh

#$SPARK_HOME/bin/spark-submit \
#    --master spark://$(hostname):7077 \
#    --driver-memory $(($RAM_MASTER))G \
#    --executor-memory $(($RAM_PER_EXECUTOR))G \
#    --total-executor-cores $(($CORES_PER_EXECUTOR * $NUM_WORKERS)) \
#    $METAPIPE_MAIN_DIR/workflow-assembly-0.1-SNAPSHOT.jar \
#        execution-manager --executor-id test-executor --num-partitions 1000 \
#        --config-file $METAPIPE_MAIN_DIR/.metapipe/conf.json --job-tags csc-test

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
        execution-manager --executor-id test-executor --num-partitions 10000 \
        --config-file .metapipe/conf.json --job-tags $SPARK_JOB_TAG

stop-all.sh





