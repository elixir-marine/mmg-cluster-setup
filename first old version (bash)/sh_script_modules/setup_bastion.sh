
ssh-keygen -R $BASTION_IP
ssh -o StrictHostKeyChecking=no -o BatchMode=yes cloud-user@$BASTION_IP " cd ~ "
while [ $? -ne 0 ]; do
  sleep 1
  ssh -o StrictHostKeyChecking=no -o BatchMode=yes cloud-user@$BASTION_IP " cd ~ "
done
ssh cloud-user@$BASTION_IP "
sudo yum install -y dstat lsof bash-completion time tmux git xauth screen nano vim bind-utils nmap-ncat git xauth firefox centos-release-openstack  python-novaclient python-openstackclient python-devel python-setuptools python-virtualenvwrapper libffi-devel openssl-devel
sudo yum groupinstall -y 'Development Tools'
sudo yum update -y
sudo reboot"

sleep 3
openstack server pause $BASTION_NAME
while [ $? -ne 0 ]; do
  sleep 1
  openstack server pause $BASTION_NAME 1>&- 2>&-
done
openstack server unpause $BASTION_NAME
while [ $? -ne 0 ]; do
  sleep 1
  openstack server unpause $BASTION_NAME 1>&- 2>&-
done
sleep 1

