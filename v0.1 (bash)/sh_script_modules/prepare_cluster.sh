
ssh -o StrictHostKeyChecking=no -o BatchMode=yes cloud-user@$BASTION_IP " cd ~ "
while [ $? -ne 0 ]; do
  sleep 1
  ssh -o StrictHostKeyChecking=no -o BatchMode=yes cloud-user@$BASTION_IP " cd ~ "
done
ssh cloud-user@$BASTION_IP bash -c "'
cd ~

export PATH=/usr/bin:$PATH
source /usr/bin/virtualenvwrapper.sh
mkvirtualenv --system-site-packages ansible2
pip install --upgrade pip setuptools
pip install ansible==2.1
pip install shade dnspython funcsigs functools32 '"

nova keypair-show $CLUSTER_KEY 1>&- 2>&-
if [ $? -ne 0 ]; then
  ssh cloud-user@$BASTION_IP "
  rm ~/.ssh/$CLUSTER_KEY_FILE
  rm ~/.ssh/$CLUSTER_KEY_FILE.pub "
  echo -e  'y\n\n' | ssh-keygen -q -N "" -f temp/$CLUSTER_KEY_FILE
  scp temp/$CLUSTER_KEY_FILE cloud-user@$BASTION_IP:~/.ssh && scp temp/$CLUSTER_KEY_FILE.pub cloud-user@$BASTION_IP:~/.ssh
  nova keypair-add --pub_key temp/$CLUSTER_KEY_FILE.pub $CLUSTER_KEY
  ssh cloud-user@$BASTION_IP " ssh-add ~/.ssh/$CLUSTER_KEY_FILE "
  #rm temp/$CLUSTER_KEY_FILE && rm temp/$CLUSTER_KEY_FILE.pub
fi
openstack keypair list

sleep 1

ssh cloud-user@$BASTION_IP " rm ~/.ssh/config "
GATEWAY="$(neutron subnet-show -F gateway_ip -f shell project_2000053)"
IFS='=' read -r nothing IP_2_EXCLUDE <<< "$GATEWAY"
IP_2_EXCLUDE=$(echo $IP_2_EXCLUDE | cut -d'"' -f 2)
if [ -z "$IP_2_EXCLUDE" ]; then
  echo # MUST NEVER HAPPEN
fi
IP_2_EXCLUDE=$(echo $IP_2_EXCLUDE | rev | cut -d'.' -f2- | rev)".*"
config_content=$(printf "\nHost %s\n\n    StrictHostKeyChecking no\n    IdentitiesOnly yes\n    IdentityFile ~/.ssh/%s\n\n" "$IP_2_EXCLUDE" "$CLUSTER_KEY_FILE"; printf "X")
config_content_prepared=${config_content%X}
printf %s "$config_content_prepared" > temp/config
scp temp/config cloud-user@$BASTION_IP:~/.ssh
#rm temp/config
ssh cloud-user@$BASTION_IP "
chmod 600 ~/.ssh/config
cat ~/.ssh/config"
