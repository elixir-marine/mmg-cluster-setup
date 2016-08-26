
if [ ! -z "$BASTION_IP" ]; then
    sed -i "/BASTION_IP=$BASTION_IP/c\BASTION_IP=" Vars.sh
fi

openstack server delete $BASTION_NAME
openstack server show $BASTION_NAME 1>&- 2>&-
while [ $? -eq 0 ]; do
  sleep 1
  openstack server show $BASTION_NAME 1>&- 2>&-
done
