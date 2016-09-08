
if [ ! -z "$BASTION_IP" ]; then
    ssh -o StrictHostKeyChecking=no cloud-user@$BASTION_IP "
    cd ~ && source $OPENRC_FILE
    source ~/.virtualenvs/ansible2/bin/activate && cd ~
    ansible-playbook -v ~/pouta-ansible-cluster/playbooks/hortonworks/deprovision.yml -e cluster_name=$CLUSTER_NAME -e num_nodes=$NUM_NODES -e remove_master=1 -e remove_master_volumes=1 -e remove_nodes=1 -e remove_node_volumes=1 -e remove_security_groups=0; logout"
fi

