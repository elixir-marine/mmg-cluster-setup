


# IMPORTANT, CHECK THE CORRECTNESS OF THESE PARAMETERS AND MODIFY IF NOT CORRECT:

# pouta.csc.fi -> Access & Security -> API Access -> Download OpenStack RC File -> <place the file in this folder>
OPENRC_FILE=Project_2000053-openrc.sh
# pouta.csc.fi -> Network -> Networks -> <your_project> -> Network Overview, "Name"
NETWORK_NAME=project_2000053
# pouta.csc.fi -> Network -> Networks -> <your_project> -> Network Overview, "ID"
NETWORK_ID=88d1d9e6-8038-4258-9e50-91ced4fee75c



# YOU CAN CONFIGURE / MODIFY THIS ACCORDING TO YOUR NEEDS:

BASTION_SECURITY_GROUP=bastion-metapipe
BASTION_NAME=bastion_host
BASTION_KEY=my_bastion_key
BASTION_FLAVOR=standard.xlarge

CLUSTER_NAME=csc-spark-cluster
CLUSTER_KEY=my_cluster_key
MASTER_FLAVOR=hpc-gen2.16core
MASTER_VOLUME_SIZE=200 # for the current version of Ansible script, min. 200 GB
NUM_NODES=1
NODE_FLAVOR=io.700GB
NODE_VOLUME_SIZE=200 # for the current version of Ansible script, min. 200 GB
DATA_VOLUME_TYPE=standard

AMBARI_NEW_PASSWORD=admin1234 # Ambari default password is "admin"



# NOT USED YET

NUMBER_OF_EXISTING_CLUSTERS=1
CURRENT_CLUSTER_NUMBER=1

MASTER_IMAGE=master_image
NODE_IMAGE=node_image



# DON'T TOUCH THIS:

SCRIPT_FOLDER=pouta-ansible-cluster
CLUSTER_KEY_FILE=id_rsa_csc_cluster
BASTION_AVAILABILITY_ZONE=nova
BASTION_IMAGE=CentOS-7.0

export LC_ALL=en_US.utf8

SEPARATOR1="\n==========================================================\n"
SEPARATOR2="\n==========================================================\n\n"

printf "\n\n"
source $OPENRC_FILE
if [ $? -ne 0 ]; then
    printf $SEPARATOR1"FILE DOESN'T EXIST. Ensure that you have downloaded and placed the project openrc file in the current directory, and OPENRC_FILE value in this file is set correct."$SEPARATOR2
    exit 1
fi
sleep 1
nova list 1>&- 2>&-
if [ $? -ne 0 ]; then
    printf $SEPARATOR1"ERROR. Ensure that you type in valid password and re-run the script."$SEPARATOR2
    exit 1
fi

BASTION_IP=

SERVER_GROUP_ID=

MASTER_PRIVATE_IP=


