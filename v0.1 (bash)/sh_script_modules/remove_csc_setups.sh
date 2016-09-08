
openstack security group delete $BASTION_SECURITY_GROUP
openstack security group show $BASTION_SECURITY_GROUP 1>&- 2>&-
while [ $? -eq 0 ]; do
  sleep 1
  openstack security group delete $BASTION_SECURITY_GROUP
  openstack security group show $BASTION_SECURITY_GROUP 1>&- 2>&-
done

openstack keypair delete $CLUSTER_KEY
nova keypair-show $CLUSTER_KEY 1>&- 2>&-
while [ $? -eq 0 ]; do
  sleep 1
  nova keypair-show $CLUSTER_KEY 1>&- 2>&-
done

openstack keypair delete $BASTION_KEY
nova keypair-show $BASTION_KEY 1>&- 2>&-
while [ $? -eq 0 ]; do
  sleep 1
  nova keypair-show $BASTION_KEY 1>&- 2>&-
done

#rm ~/.ssh/$PRIVATE_KEY
#rm ~/.ssh/$PUBLIC_KEY