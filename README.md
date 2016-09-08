# mmg-cluster-setup

Program tool for setting up Metapipe on openstack-based cPouta (pouta.csc.fi).

This is the 1st version of the tool.
Tested on clean installations of Kubuntu 14.04, Ubuntu 16.04,
  to be tested on other (K)Ubuntu and CentOS versions in the nearest days.

Requirements:
- Linux distribution. (K)Ubuntu 14.04 or higher, or CentOS 6.8 or higher.
- Username and password for CSC portal.
- Installed Java 8: "openjdk-8-jdk" for (K)Ubuntu, "java-1.8.0-openjdk" for CentOS.
- Firefox.

What it can do:
- Set up environment on CSC, in other words make all required preparations.
- Provision cluster that has 1 master node, X regular nodes and X nodes with ssd; with flavor, image and other parameters defined in "config.yml".
- Open access to cluster monitoring Ambari web admin for IPs registered in "config.yml".
- Run a spark test script on the cluster.
- Deprovision of existing cluster, cleanup in CSC settings.
- Vast amount of logging/debugging information displayed.

Limitations of the current version v1:
- Does not yet install Metapipe.
- Not implemented scale-up and scale-down, utilization of images.
- Only 1 cluster can exist per time.

Structure of the folder v1:
- "out": Contains the tool executable and components.
  - "config.yml": The file to be edited by user. Important that it is set up correctly.
  - "Metapipe-cPouta.jar": the executable file.
  - "cluster_test.py": test script that is executed on the cluster right after the cluster is created and configured. User can possibly replace the code in this file to test the cluster with own script.
  - "ambari-shell-commands": the script executed on the master while cluster is being configured. User can add own commands in accordance with:
  https://cwiki.apache.org/confluence/display/AMBARI/Ambari+Shell
  however, the first line must is not to be modified.
  - "ambari_csc-spark-cluster_firefox.sh" (generated in the tool folder during cluster creation): executable file that acts as a weblink-shortcut that opens Ambari web-gui in Firefox.
  - all other files/folders: other tool components, not to be modified.
- all other files/folders: Java project / source files.

2 know before running:
- When the tool is launched, the first thing it does is the validation of "config.yml", so it should detect if something is wrong, even if, for example, user wrote 1 wrong letter in the flavor name.
- However: make sure "config.yml" is configured correctly and according to the needs, pay attention to the comments in the file.

How to run:
- Method 1:
  - Open terminal in the tool folder, run the command:
  - java -jar $(pwd)/Metapipe-cPouta.jar
  - The user will be prompted for username and password from CSC.
  - When the username and password are checked, the user can start executing commands.
- Method 2:
  - Open terminal in the tool folder, run the command:
  - java -jar $(pwd)/Metapipe-cPouta.jar username=<username> password=<password>
  - When the username and password are checked, the user can start executing commands.

Commands:
- "help".
- "quit" and "exit".
- "create-env":
  Configures the cPouta environment, makes it prepared for a cluster provision.
  It spawns and configures Bastion host, creates required OpenStack parameters.
- "create-cluster":
  Creates, sets up and tests a new cluster.
- "create-all":
  The combination of "create-env" and "create-cluster".
- "test":
  Runs test script on the existing cluster. It is a part of the "create-cluster" procedure, but was implemented as a separate command to make it possible to test the cluster at any point in time, as well as to test the script itself, if the user wants to run own script.
- "remove-cluster":
  Deprovision of the existing cluster.
- "remove-all":
  Removes the existing cluster + everything that was done by "create-env". A total clean-up.
- "ip-admin-add <X.X.X.X>":
  Open access to Ambari cluster management web-gui to the provided IP address or addresses.
- "ip-admin-remove <X.X.X.X>":
  The opposite of "ip-admin-add <X.X.X.X>".


DETAILS (the variable names <X> are taken from config.yml):

"create-env":
- Bastion security setup if it was not previously done:
  - Create security group with the name <bastionSecGroupName>.
  - Create security group rule in the group <bastionSecGroupName> with the parameters: Ingress, port 22, protocol TCP, address 129.242.0.0/16.
  - Create security group rule in the group <bastionSecGroupName> with the parameters: Ingress, port -, protocol ICMP, address 0.0.0.0/0. (made for being able to ping Bastion, will be removed in later versions)
- Bastion keys setup if the key with the name <bastionKeyName> doesn't exist on CSC yet:
  - Generate keys in the .ssh folder if they don't exist, make them readable and writable for user only.
  - Register the public key on CSC.
- Create Bastion host if it doesn't exist yet:
  - Create server with name <bastionMachineName>, keyname <bastionKeyName>, security groups <bastionSecGroupName> and "default", network ID <networkId>.
- Attach a public IP address to the Bastion.
- Set up Bastion:
  - Install required packages.
  - Install required packages (procedure repeated to check the successfully installed packages and install packages that for some reason might not have been installed during the previous step).
  - Install Ansible and required python virtualenv modules.
- Create a key pair for future cluster(s):
  - Remove key with the name <clusterKeyName> from CSC if it exists.
  - Generate new key pair in the "temp" folder.
  - Register the public key with the name <clusterKeyName> on CSC.
  - Transfer the key pair to the Bastion, place in .ssh folder.
  - Change read/write policies for the contents of the .ssh folder on Bastion.
- Modify .ssh/config on Bastion:
  - Host 192.168.1.* (taken from the Bastion private IP address), StrictHostKeyChecking no, IdentitiesOnly yes, IdentityFile ~/.ssh/<clusterKeyFileName>.

"create-cluster":
- Create anti-affinity server group with the name <clusterName>-common, if it was not previously registered by the tool in config.yml.
- Generate "cluster_vars.yaml" file in the "temp" folder, using the to-be-readonly template "cluster_vars.yaml.template" and the values from config.yml.
- Update the Ambari password to <ambariPassword> in first line of the "ambari-shell-commands".
- Transfer this tool to the Bastion:
  - Put all tool components in an archive.
  - Transfer the archive to Bastion.
  - Exctact the archive on Bastion.
- Execute cluster creation on Bastion:
  - Activate virtualenv.
  - Export ANSIBLE_HOST_KEY_CHECKING as False.
  - Execute ansible cluster provision script with the file "cluster_vars.yaml".
  - Execute ansible cluster configuration script with the file "cluster_vars.yaml".
  - <INFO>:
    - Cluster deployment ansible script done by CSC-IT-Center-for-Science.
    - Script version of 07.08.2016.
    - https://github.com/CSC-IT-Center-for-Science/pouta-ansible-cluster
    - https://github.com/CSC-IT-Center-for-Science/pouta-ansible-cluster/blob/feature/heterogenous_vm_support%2324/playbooks/hortonworks/README.md
- Open access to the cluster master for all "admins":
  - Get the IP used for the internet connection on the machine where the tool is running, via <ipCheck>.
  - Add this IP to the <ipAdmins> list.
  - For all IPs in the <ipAdmins> list, create a security group rule in the group <clusterName>-master with the parameters: Ingress, port 8080, protocol TCP.
- Generate file "ambari_csc-spark-cluster_firefox.sh", which is an executable acting as a web shortcut for opening Ambari web-gui in the browser.
- Launch the transfered copy of this tool on Bastion.
- Running on Bastion:
  - Install Java 8 on cluster master.
  - Archive, transfer and extract a copy of this tool on Master.
  - Launch the transfered copy of this tool on Master.
  - Running on Master:
    - Set up:
      - Execute the "ambari-shell-0.1.31.jar" component with the commands in "ambari-shell-commands" file.
	- Change Ambari password from the default "admin" to <ambariPassword>.
	- Additional user commands, if they were added by user to "ambari-shell-commands".
	- https://cwiki.apache.org/confluence/display/AMBARI/Ambari+Shell.
      - Send start all services command to Ambari via "ambari-shell-0.1.31.jar", to make sure all Ambari services are being started.
      - Wait until all required services have status "STARTED".
	- Can be also seen on the Ambari web admin,
	- Once all required services have status "STARTED", can continue.
      - Run commands to enable Spark and HDFS for <userName>:
	- sudo -u hdfs hadoop fs -mkdir /user/<userName>
	- sudo -u hdfs hadoop fs -chown -R <userName> /user/<userName>
    - Test cluster:
      - Run Spark test "cluster_test.py" on the Master.
      - Run test "cluster_test.py" on all slave nodes.
      - <INFO>:
	- The standard script calculates the number of primes between 0 and 10000000.
	- If the user hasn't replaced the contents of "cluster_test.py" with the own script, and if the cluster was set up correctly, the message "Number of primes in range from 0 to 10000000 is: 664579" will be in the tool output, 1 message for each of the 2 tests.

"create-all":
- "create-env".
- "create-cluster".

"test":
- Archive, transfer and extract a copy of this tool on Bastion.
- Launch the transfered copy of this tool on Bastion.
- Running on Bastion:
  - Archive, transfer and extract a copy of this tool on Master.
  - Launch the transfered copy of this tool on Master.
  - Running on Master:
    - Test cluster:
      - Run Spark test "cluster_test.py" on the Master.
      - Run test "cluster_test.py" on all slave nodes.

"remove-cluster":
- Archive, transfer and extract a copy of this tool on Bastion.
- Activate virtualenv.
- Execute  ansible cluster deprovision script with the file "cluster_vars.yaml".
- Remove the cluster-related files in the tool folder and cluster-related values in "config.yml" that are no longer needed.

"remove-all":
- "remove-cluster".
- Remove Bastion:
  - Terminate Bastion instance.
  - Deallocate the IP that was occupied by Bastion.
  - Remove the Bastion-related values in "config.yml" that are no longer needed.
- Remove CSC env setups:
  - Remove Bastion security group.
  - Remove cluster keypair.
  - Remove Bastion keypair.


