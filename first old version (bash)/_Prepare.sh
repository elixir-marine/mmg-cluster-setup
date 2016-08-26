 
OS=$(lsb_release -si)
ARCH=$(uname -m | sed 's/x86_//;s/i[3-6]86/32/')
VER=$(lsb_release -sr)

SEPARATOR1="\n==========================================================\n"
SEPARATOR2="\n==========================================================\n\n"

printf "$SEPARATOR1| $OS | x$ARCH | $VER |$SEPARATOR2"

if [ "$OS" == "CentOS" ]; then
    sudo yum install centos-release-openstack-juno
    sudo yum update
    sudo yum install python-openstackclient
    sudo yum install python-novaclient
    sudo yum install python-neutronclient
    sudo yum install firefox
elif [ "$OS" == "Ubuntu" ]; then
    sudo apt-get update && sudo apt-get dist-upgrade
    sudo apt-get install python-openstackclient
    sudo apt-get install python-novaclient
    sudo apt-get install python-neutronclient
    sudo add-apt-repository ppa:ubuntu-mozilla-security/ppa
    sudo apt-get install firefox
else
    printf $SEPARATOR1"The script is meant to run on (K)Ubuntu and CentOS."$SEPARATOR2
    return
fi

printf $SEPARATOR1"Your PC is prepared, required packages are installed."$SEPARATOR2