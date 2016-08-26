
if [ ! -z "$BASTION_IP" ]; then
    ssh -o StrictHostKeyChecking=no cloud-user@$BASTION_IP "
    cd ~ && source $OPENRC_FILE
    source ~/.virtualenvs/ansible2/bin/activate && cd ~
    ansible-playbook -v ~/pouta-ansible-cluster/playbooks/hortonworks/deprovision.yml -e cluster_name=$CLUSTER_NAME -e num_nodes=$NUM_NODES -e remove_master=1 -e remove_master_volumes=1 -e remove_nodes=1 -e remove_node_volumes=1 -e remove_security_groups=1"
    if [ ! -z "$SERVER_GROUP_ID" ]; then
      nova server-group-delete $SERVER_GROUP_ID
      sed -i "/SERVER_GROUP_ID=$SERVER_GROUP_ID/c\SERVER_GROUP_ID=" Vars.sh
    fi
    if [ ! -z "$MASTER_PRIVATE_IP" ]; then
      sed -i "/MASTER_PRIVATE_IP=$MASTER_PRIVATE_IP/c\MASTER_PRIVATE_IP=" Vars.sh
    fi
fi

