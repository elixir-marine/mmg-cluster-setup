
ssh -o StrictHostKeyChecking=no cloud-user@$BASTION_IP " cd ~ "
while [ $? -ne 0 ]; do
  sleep 1
  ssh cloud-user@$BASTION_IP " cd ~ "
done

ssh cloud-user@$BASTION_IP " 
rm ~/$OPENRC_FILE
rm -r ~/$SCRIPT_FOLDER "
scp $OPENRC_FILE cloud-user@$BASTION_IP:~
scp -r $SCRIPT_FOLDER cloud-user@$BASTION_IP:~

ssh cloud-user@$BASTION_IP " rm ~/cluster_vars.yaml "
vars_content=$(printf "\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n" "cluster_name: $CLUSTER_NAME" "master_flavor: $MASTER_FLAVOR" "node_flavor: $NODE_FLAVOR" "num_nodes: $NUM_NODES" "ssh_key: $CLUSTER_KEY" "network_name: $NETWORK_NAME" "bastion_secgroup: $BASTION_SECURITY_GROUP" "master_volume_size: $MASTER_VOLUME_SIZE" "node_volume_size: $NODE_VOLUME_SIZE" "data_volume_type: $DATA_VOLUME_TYPE"; printf "X")
vars_content_prepared=${vars_content%X}
printf %s "$vars_content_prepared" > temp/cluster_vars.yaml
scp temp/cluster_vars.yaml cloud-user@$BASTION_IP:~
#rm temp/cluster_vars.yaml

SGL="$(nova server-group-list)"
while read -r line; do
  IFS=' | ' read -r part1 part2 part3 part4 <<< "$line"
  #echo $part3
  if [ "$part3" == "$CLUSTER_NAME-common" ]; then
      SERVER_GROUP_ID=$part2
      #echo "SERVER_GROUP_ID=$part2"
  fi
done <<< "$SGL"
if [ -z "$SERVER_GROUP_ID" ]; then
    nova server-group-create $CLUSTER_NAME-common anti-affinity
    while [[ -z "$SERVER_GROUP_ID" ]]; do
      unset SGL
      SGL="$(nova server-group-list)"
      while read -r line; do
	IFS=' | ' read -r part1 part2 part3 part4 <<< "$line"
	#echo $part3
	if [ "$part3" == "$CLUSTER_NAME-common" ]; then
	    SERVER_GROUP_ID=$part2
	    #echo "SERVER_GROUP_ID=$part2"
	fi
      done <<< "$SGL"
      sleep 1
    done
    echo "Cluster server group created. ID: $SERVER_GROUP_ID."
else
    echo "Cluster server group already exists. ID: $SERVER_GROUP_ID."
fi
sed -i "/SERVER_GROUP_ID=/c\SERVER_GROUP_ID=$SERVER_GROUP_ID" Vars.sh
echo "$SGL"

MESSAGE1="CLUSTER WAS CREATED."
MESSAGE2="CLUSTER WAS CONFIGURED: Spark-/HDFS-/Ambari-setup-related."
MESSAGE3="CLUSTER WAS CONFIGURED: Ambari-configuration-related."

scp ambari-shell-0.1.DEV.jar cloud-user@$BASTION_IP:~

ssh cloud-user@$BASTION_IP bash -c "'

cd ~ && cat ~/cluster_vars.yaml
source $OPENRC_FILE && nova image-list

source ~/.virtualenvs/ansible2/bin/activate && cd ~
export ANSIBLE_HOST_KEY_CHECKING=False

ansible-playbook -v -e @cluster_vars.yaml ~/$SCRIPT_FOLDER/playbooks/hortonworks/provision.yml
echo $MESSAGE1
sleep 3

ansible-playbook -v -i hortonworks-inventory ~/pouta-ansible-cluster/playbooks/hortonworks/configure.yml
echo $MESSAGE2
sleep 3

deactivate '"

MASTER_PRIVATE_IP="$(openstack server show csc-spark-cluster-master -c addresses -f shell)"
MASTER_PRIVATE_IP=$(echo $MASTER_PRIVATE_IP | cut -d'=' -f 3)
MASTER_PRIVATE_IP=$(echo $MASTER_PRIVATE_IP | cut -d'"' -f 1)
MASTER_PRIVATE_IP=$(echo $MASTER_PRIVATE_IP | cut -d',' -f 1)
echo $MASTER_PRIVATE_IP
if [ ! -z "$MASTER_PRIVATE_IP" ]; then
    sed -i "/MASTER_PRIVATE_IP=/c\MASTER_PRIVATE_IP=$MASTER_PRIVATE_IP" Vars.sh
    echo "MASTER_PRIVATE_IP: $MASTER_PRIVATE_IP."
else
    echo "MASTER_PRIVATE_IP not found. This didn't suppose to happen."
    return
fi

ssh cloud-user@$BASTION_IP "
ssh -o StrictHostKeyChecking=no -t -t cloud-user@$MASTER_PRIVATE_IP ' cd ~ '
while [ $? -ne 0 ]; do
  sleep 1
  ssh cloud-user@$MASTER_PRIVATE_IP ' cd ~ '
done"

ssh cloud-user@$BASTION_IP bash -c "'
scp ~/ambari-shell-0.1.DEV.jar cloud-user@$MASTER_PRIVATE_IP:~
ssh -t -t cloud-user@$MASTER_PRIVATE_IP << EOSSH
sudo yum install -y java-1.8.0-openjdk-devel
sleep 5
java -jar ambari-shell-0.1.DEV.jar --ambari.server=localhost --ambari.port=8080 --ambari.user=admin --ambari.password=admin
users changepassword --user admin --oldpassword admin --newpassword $AMBARI_NEW_PASSWORD --adminuser true
exit
sleep 10
while ! type 'hadoop' > /dev/null || ! hadoop || ! hadoop fs -test -d /user; do
  sleep 10
done
sudo -u hdfs hadoop fs -mkdir /user/cloud-user
while [[ $? -ne 0 ]] && ! hadoop fs -test -d /user/cloud-user; do
  sleep 10
  sudo -u hdfs hadoop fs -mkdir /user/cloud-user
done
sudo -u hdfs hadoop fs -chown -R cloud-user /user/cloud-user
sleep 1
logout
EOSSH

echo $MESSAGE3
sleep 3
'"
