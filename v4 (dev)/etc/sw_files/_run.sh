
source _init.sh

#>| $SPARK_HOME/conf/spark-env.sh
#echo "export SPARK_WORKER_INSTANCES=$EXECUTORS_PER_SLAVE" > $SPARK_HOME/conf/spark-env.sh
#echo "export SPARK_WORKER_MEMORY=$(($RAM_PER_EXECUTOR))g" >> $SPARK_HOME/conf/spark-env.sh
until echo -e "
export SPARK_WORKER_INSTANCES=$EXECUTORS_PER_SLAVE
export SPARK_WORKER_MEMORY=$(($RAM_PER_EXECUTOR))g
" > $SPARK_HOME/conf/spark-env.sh; do
    sleep 0.25
done

java -jar $SW_EXECUTABLE validate

sleep 2

if [ "$1" != "assembly" ]; then
    source _run_func_analysis.sh "$@"
else
    source _run_assembly.sh "${@:2}"
fi
