

SPARK_FILES_DIR=/export/sw_tmp
CLUSTER_NAME="csc-spark-cluster"

CORES_MASTER=8
RAM_MASTER=29

CORES_PER_SLAVE=48
RAM_PER_SLAVE=240

CORES_PER_EXECUTOR=8
EXECUTORS_PER_SLAVE=$(($CORES_PER_SLAVE / $CORES_PER_EXECUTOR))
RAM_PER_EXECUTOR=$(($RAM_PER_SLAVE / $EXECUTORS_PER_SLAVE))



unset WORKER_HOSTS
declare -a WORKER_HOSTS
while read -r x; do
    temp_str=$(printf "$x" | head -n1 | awk '{print $1;}');
    if [[ $temp_str == *"$CLUSTER_NAME"* ]]; then
      WORKER_HOSTS[${#WORKER_HOSTS[*]}]=$temp_str
    fi
done < <(/usr/sbin/arp -a)
echo "Found connected slaves:"
printf '%s\n' "${WORKER_HOSTS[@]}"

NUM_SLAVES=${#WORKER_HOSTS[*]}


export PATH=$PATH:$SPARK_FILES_DIR/scala-2.10.6/bin
export SPARK_HOME=$SPARK_FILES_DIR/spark-1.6.2-bin-hadoop2.6
export PATH=$PATH:$SPARK_HOME/bin:$SPARK_HOME/sbin


