
openstack security group create --description 'Bastion' $BASTION_SECURITY_GROUP
nova secgroup-add-rule $BASTION_SECURITY_GROUP tcp 22 22 129.242.0.0/16
nova secgroup-add-rule $BASTION_SECURITY_GROUP icmp -1 -1 0.0.0.0/0

file1=~/.ssh/id_rsa
file2=~/.ssh/id_rsa.pub
if [ -f $file1 ] && [ -f $file2 ]; then
    echo "SSH Key Pair For CSC Bastion Found: $file1 "
else
    echo "SSH Key Pair For CSC Bastion Not Found: $file1 "
    echo "Generating..."
    ssh-keygen -q -N "" -f $file1
fi
chmod 600 $file1
nova keypair-add --pub_key $file2 $BASTION_KEY
openstack keypair list

openstack server create --availability-zone $BASTION_AVAILABILITY_ZONE --flavor $BASTION_FLAVOR --image $BASTION_IMAGE --key-name $BASTION_KEY --security-group $BASTION_SECURITY_GROUP --security-group default --nic net-id=$NETWORK_ID $BASTION_NAME
openstack server pause $BASTION_NAME 1>&- 2>&-
while [ $? -ne 0 ]; do
  sleep 1
  openstack server pause $BASTION_NAME 1>&- 2>&-
done
openstack server unpause $BASTION_NAME
IPs="$(openstack ip floating list -c "IP" -c "Fixed IP" -f csv)"
while read -r line  &&  [[ -z "$BASTION_IP" ]]; do
  IFS=',' read -r part1 part2 <<< "$line"
  part1=$(echo $part1 | cut -d'"' -f 2)
  part2=$(echo $part2 | cut -d'"' -f 2)
  if [ -z "$part2" ]; then
      BASTION_IP=$part1
  fi
done <<< "$IPs"
if [ ! -z "$BASTION_IP" ]; then
    openstack ip floating add $BASTION_IP $BASTION_NAME
    sed -i "/BASTION_IP=/c\BASTION_IP=$BASTION_IP" Vars.sh
    echo "Allocated IP $BASTION_IP was assigned to $BASTION_NAME."
else
    echo $'All allocated IPs are busy. Release one or allocate a new one, and launch the script again.'
    return
fi
