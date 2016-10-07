CLUSTER_NAME="csc-spark-cluster"
AMBARI_PASSWORD="admin1234"
METAPIPE_DIR=/data/metapipe
DEPENDENCIES_PATH=$METAPIPE_DIR/metapipe/tools/blast-2.2.19/bin:$METAPIPE_DIR/metapipe/tools/interproscan-5.18-57.0:$METAPIPE_DIR/metapipe/bin

NUM_NODES=3
CORES_PER_EXECUTOR=16
CORES_PER_TASK=$(($CORES_PER_EXECUTOR / 2))
CORES_MASTER=6
RAM_PER_EXECUTOR=45
RAM_MASTER=25
RAM_PER_TASK=$(($RAM_PER_EXECUTOR / 2))


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
sudo cp conf.json $METAPIPE_DIR/metapipe
sudo mkdir $METAPIPE_DIR/metapipe-tmp
sudo chmod 777 $METAPIPE_DIR/metapipe-tmp
#sudo chown cloud-user:hdfs $METAPIPE_DIR/metapipe-tmp/

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



# Temporary solution for Interpro (the launcher is hardcoded for Stallo)
sed -i '$ d' $METAPIPE_DIR/metapipe/bin/interpro
echo "$METAPIPE_DIR/metapipe/tools/interpro/interproscan.sh" '"$@"' | sudo tee --append $METAPIPE_DIR/metapipe/bin/interpro
cat $METAPIPE_DIR/metapipe/bin/interpro

# Temporary solution for Blast Legacy (since not in Metapipe yet)
sudo tar xvf blast-2.2.19-x64-linux.tar.gz -C $METAPIPE_DIR/metapipe/tools/ --keep-old-files

# Temporary solution for Blast+ (since not in Metapipe yet)
sudo yum -y localinstall ncbi-blast-2.4.0+-2.x86_64.rpm --nogpgcheck
sudo cp ncbi-blast-2.4.0+-2.x86_64.rpm $METAPIPE_DIR
arp -a | while read -r x; do
    name=$(printf "$x" | head -n1 | awk '{print $1;}');
    if [[ $name == *"$CLUSTER_NAME"* ]]; then
      echo "$name"
      #scp ncbi-blast-2.4.0+-2.x86_64.rpm cloud-user@$name:~
      #ssh -n cloud-user@$name bash -c "'
      #  sudo yum -y localinstall ncbi-blast-2.4.0+-2.x86_64.rpm --nogpgcheck'"
      ssh -n cloud-user@$name "
        sudo yum -y localinstall $METAPIPE_DIR/ncbi-blast-2.4.0+-2.x86_64.rpm --nogpgcheck"
    fi
done


# Change some YARN Ambari parameters with help of the script provided by Ambari
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME yarn-site "yarn.scheduler.maximum-allocation-mb" "256384"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME yarn-site "yarn.scheduler.maximum-allocation-vcores" "1024"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME yarn-site "yarn.nodemanager.resource.cpu-vcores" "$CORES_PER_EXECUTOR"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME yarn-site "yarn.nodemanager.resource.memory-mb" "$RAM_PER_EXECUTOR"

# Stop YARN Ambari service
unset RESPONSE
while [[ $RESPONSE != *'"status" : "Accepted"'* ]]; do
   RESPONSE=$(curl -u admin:$AMBARI_PASSWORD -i -H 'X-Requested-By: ambari' -X PUT -d '{"RequestInfo": {"context" :"Stop YARN via REST"}, "Body": {"ServiceInfo": {"state": "INSTALLED"}}}' http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/YARN)
   sleep 3
done

# Wait until stopped
unset RESPONSE
while [[ $RESPONSE != *'"state" : "INSTALLED"'* ]]; do
   RESPONSE=$(curl -k -u admin:$AMBARI_PASSWORD -H 'X-Requested-By: ambari' -X GET http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/YARN)
   sleep 5
done

# Start YARN Ambari service
unset RESPONSE
while [[ $RESPONSE != *'"status" : "Accepted"'* ]]; do
   RESPONSE=$(curl -u admin:$AMBARI_PASSWORD -i -H 'X-Requested-By: ambari' -X PUT -d '{"RequestInfo": {"context" :"Start YARN via REST"}, "Body": {"ServiceInfo": {"state": "STARTED"}}}' http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/YARN)
   sleep 3
done

# Wait until started
unset RESPONSE
while [[ $RESPONSE != *'"state" : "STARTED"'* ]]; do
   RESPONSE=$(curl -k -u admin:$AMBARI_PASSWORD -H 'X-Requested-By: ambari' -X GET http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/YARN)
   sleep 5
done


# Change some SPARK Ambari parameters with help of the script provided by Ambari
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.driver.cores" "$CORES_MASTER"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.yarn.am.cores" "$CORES_MASTER"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.driver.memory" "$(($RAM_MASTER))g"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.yarn.am.memory" "$(($RAM_MASTER))g"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.executor.instances" "$NUM_NODES"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.executor.cores" "$CORES_PER_EXECUTOR"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.executor.memory" "$(($RAM_PER_EXECUTOR))g"
#/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.cores.max" "1024"
/var/lib/ambari-server/resources/scripts/configs.sh -u admin -p $AMBARI_PASSWORD set localhost $CLUSTER_NAME spark-defaults "spark.task.cpus" "$CORES_PER_TASK"

# Stop SPARK Ambari service
unset RESPONSE
while [[ $RESPONSE != *'"status" : "Accepted"'* ]]; do
   RESPONSE=$(curl -u admin:$AMBARI_PASSWORD -i -H 'X-Requested-By: ambari' -X PUT -d '{"RequestInfo": {"context" :"Stop SPARK via REST"}, "Body": {"ServiceInfo": {"state": "INSTALLED"}}}' http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/SPARK)
   sleep 3
done

# Wait until stopped
unset RESPONSE
while [[ $RESPONSE != *'"state" : "INSTALLED"'* ]]; do
   RESPONSE=$(curl -k -u admin:$AMBARI_PASSWORD -H 'X-Requested-By: ambari' -X GET http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/SPARK)
   sleep 5
done

# Start SPARK Ambari service
unset RESPONSE
while [[ $RESPONSE != *'"status" : "Accepted"'* ]]; do
   RESPONSE=$(curl -u admin:$AMBARI_PASSWORD -i -H 'X-Requested-By: ambari' -X PUT -d '{"RequestInfo": {"context" :"Start SPARK via REST"}, "Body": {"ServiceInfo": {"state": "STARTED"}}}' http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/SPARK)
   sleep 3
done

# Wait until started
unset RESPONSE
while [[ $RESPONSE != *'"state" : "STARTED"'* ]]; do
   RESPONSE=$(curl -k -u admin:$AMBARI_PASSWORD -H 'X-Requested-By: ambari' -X GET http://$CLUSTER_NAME-master-1.novalocal:8080/api/v1/clusters/$CLUSTER_NAME/services/SPARK)
   sleep 5
done


# 4 future service implementation?
#sudo cp metapipe.service /etc/systemd/system.#sudo systemctl enable /etc/systemd/system/metapipe.service
#sudo cp yarn-site.xml /etc/hadoop/conf/
#sudo chown -R cloud-user:cloud-user /etc/hadoop/conf/yarn-site.xml
#sudo chmod 444 /etc/hadoop/conf/yarn-site.xml
#sudo chattr +i /etc/hadoop/conf/yarn-site.xml


#sudo echo 'export PATH=/home/cloud-user/metapipe/tools/interproscan-5.18-57.0:$PATH' >> ~/.bashrc
#sudo echo 'export PATH=/home/cloud-user/metapipe/bin:$PATH' >> ~/.bashrc
echo "HADOOP_USER_NAME=hdfs" | sudo tee --append /etc/environment.echo "export HADOOP_USER_NAME=hdfs" | sudo tee --append /etc/bash_profile
echo "export HADOOP_USER_NAME=hdfs" | sudo tee --append /etc/bashrc.echo "export HADOOP_USER_NAME=hdfs" | sudo tee --append /etc/profile

echo "PATH=$DEPENDENCIES_PATH:/usr/lib64/qt-3.3/bin:/usr/lib64/ccache:/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:$PATH" | sudo tee --append /etc/environment
echo "export PATH=$DEPENDENCIES_PATH:\$PATH" | sudo tee --append /etc/bashrc
echo "export PATH=$DEPENDENCIES_PATH:\$PATH" | sudo tee --append /etc/bash_profile
echo "export PATH=$DEPENDENCIES_PATH:\$PATH" | sudo tee --append /etc/profile


# Running preparations each worker (instances that are accessible from Master via network and have "CLUSTER_NAME*" in their host name)
arp -a | while read -r x; do
    name=$(printf "$x" | head -n1 | awk '{print $1;}');
    if [[ $name == *"$CLUSTER_NAME"* ]]; then
      echo "$name"
      ssh -n cloud-user@$name bash -c "'
        echo "HADOOP_USER_NAME=hdfs" | sudo tee --append /etc/environment > /dev/null
        echo "export HADOOP_USER_NAME=hdfs" | sudo tee --append /etc/bash_profile > /dev/null
        echo "export HADOOP_USER_NAME=hdfs" | sudo tee --append /etc/bashrc > /dev/null
        echo "export HADOOP_USER_NAME=hdfs" | sudo tee --append /etc/profile > /dev/null
        echo "PATH=$DEPENDENCIES_PATH:/usr/lib64/qt-3.3/bin:/usr/lib64/ccache:/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin" | sudo tee --append /etc/environment > /dev/null
        echo "export PATH=$DEPENDENCIES_PATH:\$PATH" | sudo tee --append /etc/bashrc > /dev/null
        echo "export PATH=$DEPENDENCIES_PATH:\$PATH" | sudo tee --append /etc/bash_profile > /dev/null
        echo "export PATH=$DEPENDENCIES_PATH:\$PATH" | sudo tee --append /etc/profile > /dev/null
        '"
    fi
done



