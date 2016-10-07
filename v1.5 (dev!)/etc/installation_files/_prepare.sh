
CLUSTER_NAME="csc-spark-cluster"
AMBARI_PASSWORD="admin1234"
METAPIPE_DIR=/data/metapipe
DEPENDENCIES_PATH=$METAPIPE_DIR/metapipe/tools/blast-2.2.19/bin:$METAPIPE_DIR/metapipe/tools/interproscan-5.18-57.0:$METAPIPE_DIR/metapipe/bin

CORES_MASTER=8
RAM_MASTER=29
NUM_WORKERS=5
CORES_PER_EXECUTOR=16
RAM_PER_EXECUTOR=58

#CORES_MASTER=$(($CORES_MASTER - 1))
#RAM_MASTER=$(($RAM_MASTER - 5))
#CORES_PER_TASK=$(($(($CORES_PER_EXECUTOR - 2)) / 2))
#RAM_PER_TASK=$(($(($RAM_PER_EXECUTOR - 10)) / 2))

CORES_PER_CONTAINER=$(($CORES_MASTER - 0))
RAM_PER_CONTAINER=$(($RAM_MASTER - $(($RAM_MASTER / 10))))
RAM_PER_SPARK=$(($RAM_PER_CONTAINER - $(($RAM_PER_CONTAINER / 8))))
RAM_PER_EXECUTOR_TOTAL=$(($RAM_PER_EXECUTOR - $(($RAM_PER_EXECUTOR / 10))))

unset WORKER_HOSTS
declare -a WORKER_HOSTS
while read -r x; do
    temp_str=$(printf "$x" | head -n1 | awk '{print $1;}');
    if [[ $temp_str == *"$CLUSTER_NAME"* ]]; then
      WORKER_HOSTS[${#WORKER_HOSTS[*]}]=$temp_str
      echo ${WORKER_HOSTS[$((${#WORKER_HOSTS[*]} - 1))]} ${#WORKER_HOSTS[*]} $temp_str
    fi
done < <(arp -a)
#echo ${WORKER_HOSTS[@]}
echo "Found connected workers:"
printf '%s\n' "${WORKER_HOSTS[@]}"



curl -u admin:$AMBARI_PASSWORD http://86.50.169.68:8080/api/v1/clusters/csc-spark-cluster/configurations?type=yarn-site&tag=version1475676696845

curl -u admin:$AMBARI_PASSWORD -i -H 'X-Requested-By: ambari' -X PUT -d '{
  "href" : "http://86.50.169.68:8080/api/v1/clusters/csc-spark-cluster/configurations?type=yarn-site&tag=version1475676696845",
  "items" : [
    {
      "href" : "http://86.50.169.68:8080/api/v1/clusters/csc-spark-cluster/configurations?type=yarn-site&tag=version1475676696845",
      "tag" : "version1475676696845",
      "type" : "yarn-site",
      "version" : 17,
      "Config" : {
        "cluster_name" : "csc-spark-cluster",
        "stack_id" : "HDP-2.3"
      },
      "properties" : {
        "yarn.nodemanager.local-dirs" : "/hadoop/scratch/yarn/local",
        "yarn.nodemanager.resource.cpu-vcores" : "7",
        "yarn.nodemanager.resource.memory-mb" : "55000",
        "yarn.nodemanager.resource.percentage-physical-cpu-limit" : "100",
        "yarn.scheduler.maximum-allocation-mb" : "26000",
        "yarn.scheduler.maximum-allocation-vcores" : "7",
        "yarn.scheduler.minimum-allocation-mb" : "26000",
        "yarn.scheduler.minimum-allocation-vcores" : "7"
      }
    }
  ]
}' http://86.50.169.68:8080/api/v1/clusters/csc-spark-cluster/configurations?type=yarn-site&tag=version1475676696845

# Change some YARN Ambari parameters with help of the script provided by Ambari
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME yarn-site "yarn.nodemanager.resource.memory-mb" "$(($RAM_PER_EXECUTOR_TOTAL * 1024))"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME yarn-site "yarn.scheduler.maximum-allocation-mb" "$(($RAM_PER_CONTAINER * 1024))"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME yarn-site "yarn.scheduler.minimum-allocation-mb" "$(($RAM_PER_CONTAINER * 1024))"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME yarn-site "yarn.nodemanager.resource.percentage-physical-cpu-limit" "100"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME yarn-site "yarn.nodemanager.resource.cpu-vcores" "$CORES_PER_EXECUTOR"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME yarn-site "yarn.scheduler.minimum-allocation-vcores" "$CORES_PER_CONTAINER"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME yarn-site "yarn.scheduler.maximum-allocation-vcores" "$CORES_PER_CONTAINER"

# Change some SPARK Ambari parameters with help of the script provided by Ambari
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.driver.cores" "$CORES_PER_CONTAINER"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.yarn.am.cores" "$CORES_PER_CONTAINER"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.driver.memory" "$(($RAM_PER_SPARK))g"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.yarn.am.memory" "$(($RAM_PER_SPARK))g"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.executor.instances" "$(($NUM_WORKERS * 2))"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.executor.cores" "$CORES_PER_CONTAINER"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.executor.memory" "$(($RAM_PER_SPARK))g"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.task.cpus" "$CORES_PER_CONTAINER"


# Stop YARN Ambari service
unset RESPONSE
while [[ $RESPONSE != *'"status" : "Accepted"'* ]]; do
   RESPONSE=$(curl -u admin:$AMBARI_PASSWORD -i -H 'X-Requested-By: ambari' -X PUT -d '{"RequestInfo": {"context" :"Stop YARN via REST"}, "Body": {"ServiceInfo": {"state": "INSTALLED"}}}' http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/YARN)
   sleep 3
done

# Stop SPARK Ambari service
unset RESPONSE
while [[ $RESPONSE != *'"status" : "Accepted"'* ]]; do
   RESPONSE=$(curl -u admin:$AMBARI_PASSWORD -i -H 'X-Requested-By: ambari' -X PUT -d '{"RequestInfo": {"context" :"Stop SPARK via REST"}, "Body": {"ServiceInfo": {"state": "INSTALLED"}}}' http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/SPARK)
   sleep 3
done

# Wait until YARN stopped
unset RESPONSE
while [[ $RESPONSE != *'"state" : "INSTALLED"'* ]]; do
   RESPONSE=$(curl -k -u admin:$AMBARI_PASSWORD -H 'X-Requested-By: ambari' -X GET http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/YARN)
   sleep 5
done

# Wait until SPARK stopped
unset RESPONSE
while [[ $RESPONSE != *'"state" : "INSTALLED"'* ]]; do
   RESPONSE=$(curl -k -u admin:$AMBARI_PASSWORD -H 'X-Requested-By: ambari' -X GET http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/SPARK)
   sleep 5
done

# Start YARN Ambari service
unset RESPONSE
while [[ $RESPONSE != *'"status" : "Accepted"'* ]]; do
   RESPONSE=$(curl -u admin:$AMBARI_PASSWORD -i -H 'X-Requested-By: ambari' -X PUT -d '{"RequestInfo": {"context" :"Start YARN via REST"}, "Body": {"ServiceInfo": {"state": "STARTED"}}}' http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/YARN)
   sleep 3
done

# Start SPARK Ambari service
unset RESPONSE
while [[ $RESPONSE != *'"status" : "Accepted"'* ]]; do
   RESPONSE=$(curl -u admin:$AMBARI_PASSWORD -i -H 'X-Requested-By: ambari' -X PUT -d '{"RequestInfo": {"context" :"Start SPARK via REST"}, "Body": {"ServiceInfo": {"state": "STARTED"}}}' http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/SPARK)
   sleep 3
done

# Wait until YARN started
unset RESPONSE
while [[ $RESPONSE != *'"state" : "STARTED"'* ]]; do
   RESPONSE=$(curl -k -u admin:$AMBARI_PASSWORD -H 'X-Requested-By: ambari' -X GET http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/YARN)
   sleep 5
done

# Wait until SPARK started
unset RESPONSE
while [[ $RESPONSE != *'"state" : "STARTED"'* ]]; do
   RESPONSE=$(curl -k -u admin:$AMBARI_PASSWORD -H 'X-Requested-By: ambari' -X GET http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/SPARK)
   sleep 5
done



sudo mkdir $METAPIPE_DIR
sudo chmod 777 $METAPIPE_DIR
if [ "$1" != "skip-unpack" ]
  then
    sudo tar xvf metapipe-deps-current.tar.gz -C $METAPIPE_DIR --keep-old-files
    #tar -I pigz -xvf metapipe-deps-current.tar.gz -C ~ --keep-old-files
fi



sudo cp _prepare.sh $METAPIPE_DIR
sudo cp _run.sh $METAPIPE_DIR
sudo cp workflow-assembly-0.1-SNAPSHOT.jar $METAPIPE_DIR
sudo mkdir $METAPIPE_DIR/.metapipe
sudo cp conf.json $METAPIPE_DIR/.metapipe
#sudo chmod 777 $METAPIPE_DIR/.metapipe
sudo cp conf.json $METAPIPE_DIR/metapipe
#sudo chmod 777 $METAPIPE_DIR/metapipe
sudo mkdir $METAPIPE_DIR/metapipe-tmp
#sudo chmod 777 $METAPIPE_DIR/metapipe-tmp
#sudo chown cloud-user:hdfs $METAPIPE_DIR/metapipe-tmp/
sudo chmod 777 $METAPIPE_DIR/*

sudo chmod 777 /home/cloud-user
#sudo chown cloud-user:hadoop /home/cloud-user

sudo ln -s $METAPIPE_DIR/.metapipe/ /home/hdfs/
sudo ln -s $METAPIPE_DIR/metapipe/ /home/hdfs/
sudo ln -s $METAPIPE_DIR/metapipe-tmp/ /home/hdfs/
sudo ln -s $METAPIPE_DIR/workflow-assembly-0.1-SNAPSHOT.jar /home/hdfs/
sudo ln -s $METAPIPE_DIR/.metapipe/ /home/cloud-user/
sudo ln -s $METAPIPE_DIR/metapipe/ /home/cloud-user/
sudo ln -s $METAPIPE_DIR/metapipe-tmp/ /home/cloud-user/
sudo ln -s $METAPIPE_DIR/workflow-assembly-0.1-SNAPSHOT.jar /home/cloud-user/
#sudo chmod 777 /home/cloud-user/.metapipe
#sudo chmod 777 /home/cloud-user/metapipe
#sudo chmod 777 /home/cloud-user/metapipe-tmp
#sudo chmod 777 /home/cloud-user/workflow-assembly-0.1-SNAPSHOT.jar



cat << EOT > ~/.ssh/config
Host *
StrictHostKeyChecking no
ConnectTimeout 15
UserKnownHostsFile /dev/null
EOT
chmod 600 ~/.ssh/config
#chmod 600 ~/.ssh/id_rsa
#chmod 644 ~/.ssh/id_rsa.pub.chmod 644 ~/.ssh/authorized_keys
sudo cp ~/.ssh/* ~/../hdfs/.ssh/
sudo chown -R hdfs:hadoop ~/../hdfs/.ssh/



# Temporary solution for Interpro (the launcher is hardcoded for Stallo)
sed -i '$ d' $METAPIPE_DIR/metapipe/bin/interpro
echo "$METAPIPE_DIR/metapipe/tools/interpro/interproscan.sh" '"$@"' | sudo tee --append $METAPIPE_DIR/metapipe/bin/interpro
cat $METAPIPE_DIR/metapipe/bin/interpro

# Temporary solution for Blast Legacy (since not in Metapipe yet)
sudo tar xvf blast-2.2.19-x64-linux.tar.gz -C $METAPIPE_DIR/metapipe/tools/ --keep-old-files

# Temporary solution for Blast+ (since not in Metapipe yet)
sudo yum -y localinstall ncbi-blast-2.4.0+-2.x86_64.rpm --nogpgcheck
sudo cp ncbi-blast-2.4.0+-2.x86_64.rpm $METAPIPE_DIR
for name in "${WORKER_HOSTS[@]}"; do
    echo "$name"
    ssh -n cloud-user@$name "
    sudo yum -y localinstall $METAPIPE_DIR/ncbi-blast-2.4.0+-2.x86_64.rpm --nogpgcheck"
done



# 4 future service implementation?
#sudo cp metapipe.service /etc/systemd/system.#sudo systemctl enable /etc/systemd/system/metapipe.service
#sudo cp yarn-site.xml /etc/hadoop/conf/
#sudo chown -R cloud-user:cloud-user /etc/hadoop/conf/yarn-site.xml
#sudo chmod 444 /etc/hadoop/conf/yarn-site.xml
#sudo chattr +i /etc/hadoop/conf/yarn-site.xml







cd /data

sudo yum install wget -y
sudo wget http://downloads.lightbend.com/scala/2.11.8/scala-2.11.8.tgz
sudo tar xvf scala-2.11.8.tgz
export PATH=$PATH:/data/scala-2.11.8/bin
scala -version

sudo wget http://d3kbcqa49mib13.cloudfront.net/spark-2.0.1-bin-hadoop2.7.tgz
sudo tar xvf spark-2.0.1-bin-hadoop2.7.tgz

export SPARK_HOME=/data/spark-2.0.1-bin-hadoop2.7
export PATH=$PATH:$SPARK_HOME/bin:$SPARK_HOME/sbin

#echo 'export PATH=$PATH:/usr/lib/scala/bin' >> .bash_profile
#echo 'export SPARK_HOME=/data/spark-2.0.1-bin-hadoop2.7' >> .bash_profile
#echo 'export PATH=$PATH:$SPARK_HOME/bin' >> .bash_profile

sudo chmod 777 -R /data/*

for name in "${WORKER_HOSTS[@]}"; do
    echo "$name" >> $SPARK_HOME/conf/slaves
done
cat $SPARK_HOME/conf/slaves

#echo "spark.driver.cores" "$CORES_PER_CONTAINER" >> $SPARK_HOME/conf/spark-defaults.conf
#echo "spark.yarn.am.cores" "$CORES_PER_CONTAINER" >> $SPARK_HOME/conf/spark-defaults.conf
#echo "spark.driver.memory" "$(($RAM_PER_SPARK))g" >> $SPARK_HOME/conf/spark-defaults.conf
#echo "spark.yarn.am.memory" "$(($RAM_PER_SPARK))g" >> $SPARK_HOME/conf/spark-defaults.conf
#echo "spark.executor.instances" "$(($NUM_WORKERS * 2))" >> $SPARK_HOME/conf/spark-defaults.conf
#echo "spark.executor.cores" "$CORES_PER_CONTAINER" >> $SPARK_HOME/conf/spark-defaults.conf
#echo "spark.executor.memory" "$(($RAM_PER_SPARK))g" >> $SPARK_HOME/conf/spark-defaults.conf
#echo "spark.task.cpus" "$CORES_PER_CONTAINER" >> $SPARK_HOME/conf/spark-defaults.conf

echo "export HADOOP_CONF_DIR=/etc/hadoop/conf" >> $SPARK_HOME/conf/spark-env.sh
echo "export YARN_CONF_DIR=/etc/hadoop/conf" >> $SPARK_HOME/conf/spark-env.sh
echo "export HADOOP_USER_NAME=hdfs" >> $SPARK_HOME/conf/spark-env.sh
#export SPARK_YARN_USER_ENV="PATH=$PATH"
echo "METAPIPE_DIR=/data/metapipe" >> $SPARK_HOME/conf/spark-env.sh
echo "DEPENDENCIES_PATH=$METAPIPE_DIR/metapipe/tools/blast-2.2.19/bin:$METAPIPE_DIR/metapipe/tools/interproscan-5.18-57.0:$METAPIPE_DIR/metapipe/bin" >> $SPARK_HOME/conf/spark-env.sh
echo 'export PATH=$DEPENDENCIES_PATH:$PATH' >> $SPARK_HOME/conf/spark-env.sh

echo "export SPARK_EXECUTOR_INSTANCES=2" >> $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_EXECUTOR_CORES=$CORES_PER_CONTAINER" >> $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_EXECUTOR_MEMORY=$(($RAM_PER_EXECUTOR_TOTAL))g" >> $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_WORKER_INSTANCES=2" >> $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_WORKER_CORES=$CORES_PER_CONTAINER" >> $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_WORKER_MEMORY=$(($RAM_PER_EXECUTOR_TOTAL))g" >> $SPARK_HOME/conf/spark-env.sh



$SPARK_HOME/bin/spark-submit $METAPIPE_DIR/workflow-assembly-0.1-SNAPSHOT.jar execution-manager --executor-id test-executor --num-partitions 1000 --config-file $METAPIPE_DIR/.metapipe/conf.json --job-tags csc-test














