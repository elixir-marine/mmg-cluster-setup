# mmg-cluster-setup

Program tool for setting up Metapipe on openstack-based cPouta (pouta.csc.fi).

Was tested on clean installation of Kubuntu 14.04/16.04, Ubuntu 12.04/14.04/16.04.

Requirements:
- Linux distribution. Preferably (K)Ubuntu, v12.04 or higher.
- Username and password for CSC portal.
- Installed Java 8: "openjdk-8-jdk" for (K)Ubuntu, "java-1.8.0-openjdk" for CentOS.
- Firefox (for viewing cluster monitoring web-gui).

What it is and what it can do:
- Command-line Java tool that executes complex procedures with 1 or few commands.
- Set up environment on CSC, make required preparations.
- Create a volume for storing Piping software, upload the software to the disk.
- Provision cluster that has 1 master node, X regular nodes and X nodes with ssd (parameters defined in "config.yml"), configure NFS.
- Open access to cluster monitoring gui for IPs registered in "config.yml".
- Setup and configure Spark in standalone mode to make it use all available resources, run a spark test script on the cluster.
- Unpack, prepare and run Piping software on the cluster.
- Deprovision of existing cluster, cleanup in CSC settings.
- Vast amount of logging/debugging information displayed.

Current limitations / 2-be-done in the future:
- Caching using snapshots not implemented yet.
- The use of anti-affinity groups is disabled until it is implemented in Ansible 2.2.
- Only 1 bastion/cluster can exist per time.
- Setup and running Piping software as a system service not implemented yet.
- Piping software upload/update from a web-source not implemented yet, done locally from the client machine.
- 1 Pipe job can run per time (on all available resources).

Structure of the tool folder "v2":
- "out": Contains the tool executable and components.
  - "config.yml": The file to be edited by user. Important that it is set up correctly.
  - "Metapipe-cPouta.jar": the executable file.
  - "_disk.sh": a helper script for disk operations on the volumes attached to the VM where the helper runs.
  - "_setup_cluster.sh" & "_init.sh": small script that sets up Spark Standalone and is run on master.
  - "cluster_test.py": test script that is executed on the cluster right after the cluster is created and configured. User can possibly replace the code in this file to test the cluster with own script.
  - "*_firefox.sh" (generated in the tool folder during cluster creation): executable file that acts as a weblink-shortcut that opens Spark monitoring web-gui in Firefox.
  - "pouta-ansible-cluster" folder: cluster provision/deprovision ansible script provided by CSC-IT-Center-for-Science, modified version of 07.08.2016. Not to be modified.
    - https://github.com/CSC-IT-Center-for-Science/pouta-ansible-cluster
    - https://github.com/CSC-IT-Center-for-Science/pouta-ansible-cluster/blob/feature/heterogenous_vm_support%2324/playbooks/hortonworks/README.md
  - "installation_files": Contains Piping software (not present on the repo) and small bash-scripts, some of which are required for the sw setup:
    - "_prepare.sh": The Piping sw preparation (unpacking, placement, configuration) is done here. In the particular case, it unpacks META-pipe to one of the NFS-shared dirs on master, and configures it and the OS for running META-pipe on Spark.
    - "_run.sh": Is used to launch installed Piping sw. In the particular case, it launches META-pipe, either functional analyses ("_run_execution.sh"), or assembly ("_run_assembly.sh") which is not ready to be used on cPouta yet.
    - "_test.sh": Used to test/validate installed sw. In the particular case, it runs META-pipe validation.
    - "_init.sh": Init some env variables for the 3 scripts above.
    - (not present in the Repo) "workflow-assembly-0.1-SNAPSHOT.jar" - META-pipe jar executable. Currently, version 18.
    - (not present in the Repo) "metapipe-deps-current.tar.gz" - META-pipe dependencies/databases. Curently, version 10.
  - all other files/folders: other tool components, not to be modified.
- all other files/folders: Java project / source files and components.

2 know before running:
- When the tool is launched, the first thing it does is the validation of "config.yml", so it should detect if something is wrong, even if, for example, user wrote 1 wrong letter in the flavor name.
- However, make sure "config.yml" is configured correctly and according to the needs, pay attention to the comments in the file.

How to run:
- Method 1:
  - Open terminal in the tool folder, run the command:
  - java -jar Metapipe-cPouta.jar
  - The user will be prompted for username and password from CSC.
  - When the username and password are checked, the user can start executing commands.
- Method 2:
  - Open terminal in the tool folder, run the command:
  - java -jar Metapipe-cPouta.jar username={username} password={password}
  - When the username and password are checked, the user can start executing commands.

Commands:
- "help".
- "quit" and "exit".
- "create-env":
  Configures the cPouta environment, makes it prepared for a cluster provision.
  It spawns and configures Bastion host, creates required OpenStack parameters, creates a disk for Piping sw storage and uploads the sw.
- "create-cluster":
  Creates VMs, disks and security settings for the cluster.
  Sets up NFS, Spark Standalone cluster, runs test script on the cluster.
  Unpacks from the sw-volume, sets up and valiates Piping software on the master VM.
- "create-all":
  The combination of "create-env" and "create-cluster".
- "test":
  Runs test script on the existing cluster, runs sw validation. A part of the "create-cluster" procedure, implemented also as a separate command to make it possible to test the stuff any time.
- "launch":
  Launches the installed Piping sw by running "_run.sh" file on master, holds the session until the sw finishes and exists.
- "remove-cluster":
  Remove the existing cluster.
- "remove-all":
  Removes the existing cluster + everything that was done by "create-env". A total clean-up.
- "ip-admin-add X.X.X.X":
  Open access to Ambari cluster management web-gui to the provided IP address or addresses.
- "ip-admin-remove X.X.X.X":
  The opposite of "ip-admin-add X.X.X.X".


DETAILS (the variable names {X} are taken from config.yml):

"create-env":
- Bastion security setup if it was not previously done:
  - Create security group with the name {bastionSecGroupName}.
  - Create security group rule in the group {bastionSecGroupName} with the parameters: Ingress, port 22, protocol TCP, address 129.242.0.0/16.
  - Create security group rule in the group {bastionSecGroupName} with the parameters: Ingress, port -, protocol ICMP, address 0.0.0.0/0. (made for being able to ping Bastion, will be removed in later versions)
- Bastion keys setup if the key with the name {bastionKeyName} doesn't exist on CSC yet:
  - Generate keys in the .ssh folder if they don't exist, make them readable and writable for user only.
  - Register the public key on CSC.
- Create Bastion host if it doesn't exist yet:
  - Create server with name {bastionMachineName}, keyname {bastionKeyName}, security groups {bastionSecGroupName} and "default", network ID {networkId}.
- Attach a public IP address to the Bastion.
- Set up Bastion:
  - Install required packages.
  - Install Ansible and required python virtualenv modules.
- Create a key pair for future cluster(s):
  - Remove key with the name {clusterKeyName} from CSC if it exists.
  - Generate new key pair in the "temp" folder.
  - Register the public key with the name {clusterKeyName} on CSC.
  - Transfer the key pair to the Bastion, place in .ssh folder.
  - Change read/write policies for the contents of the .ssh folder on Bastion.
- Modify .ssh/config on Bastion:
  - Host 192.168.1.* (taken from the Bastion private IP address), StrictHostKeyChecking no, IdentitiesOnly yes, IdentityFile ~/.ssh/{clusterKeyFileName}.
- Transfer the tool to Bastion for future execution of bastion routine.
  - Put all tool components in an archive.
  - Transfer the archive to Bastion.
  - Exctact the archive on Bastion.
- Piping software volume preparation:
  - Create the volume.
  - Attach to Bastion, create a file system, detach.
  - Attach to Bastion, mount, transfer the files in "installation_files" to the disk, unmount and detach.

"create-cluster":
- Generate "cluster_vars.yaml" file in the "temp" folder, using the to-be-readonly template "cluster_vars.yaml.template" and the values from config.yml.
- Transfer this tool to the Bastion.
- Attach and mount the sw-disk to Bastion, update the Piping sw related scripts from "installation_files", unmount and detach.
- Execute cluster provision on Bastion:
  - Export ANSIBLE_HOST_KEY_CHECKING as False.
  - Execute ansible cluster provision script with the file "cluster_vars.yaml".
  - Execute ansible cluster configuration script with the file "cluster_vars.yaml".
- Open access to the cluster master for all "admins":
  - Get the IP used for the internet connection on the machine where the tool is running, via {ipCheck}.
  - Add this IP to the {ipAdmins} list.
  - For all IPs in the {ipAdmins} list, create a security group rule in the group {clusterName}-master with the parameters: Ingress, port 8080, protocol TCP.
- Generate file "csc-spark-cluster_firefox.sh", which is an executable acting as a web shortcut for opening Ambari web-gui in the browser.
- Launch the transfered copy of this tool on Bastion.
- Running on Bastion:
  - Check if Java 8 is present on cluster master after the cluster provision ansible was run, install if missing for some reason.
  - Archive, transfer and extract a copy of this tool on Master.
  - Launch "_setup_cluster.sh" script on master:
    - Install Scala, Spark.
    - Register Spark slave nodes.
    - Set hw-resource-related Spark values.
  - Run "_setup_cluster.sh test cluster_test.py" on master to test the cluster.
  - Prepare Piping sw:
    - Attach and mount the sw disk to master.
    - Run the responsible script "_prepare.sh".
    - The sw is now on master in one of the NFS-shared olders, configured and ready to run.
    - Unmount and detach sw-disk from master.
  - Run "_test.sh" script that is responsible for the Piping sw test or validation.

"create-all":
- "create-env".
- "create-cluster".

"test":
- Archive, transfer and extract a copy of this tool on Bastion.
- Attach and mount the sw-disk to Bastion, update the Piping sw related scripts from "installation_files", unmount and detach.
- Launch the transfered copy of this tool on Bastion.
- Running on Bastion:
  - Archive, transfer and extract a copy of this tool on Master.
  - Attach and mount the sw-disk to Master, update the Piping sw related scripts from "installation_files", unmount and detach.
  - Run "_setup_cluster.sh test cluster_test.py" on master to test the cluster.
  - Run "_test.sh" script that is responsible for the Piping sw test or validation.

"launch":
- Archive, transfer and extract a copy of this tool on Bastion.
- Attach and mount the sw-disk to Bastion, update the Piping sw related scripts from "installation_files", unmount and detach.
- Launch the transfered copy of this tool on Bastion.
- Running on Bastion:
  - Archive, transfer and extract a copy of this tool on Master.
  - Attach and mount the sw-disk to Master, update the Piping sw related scripts from "installation_files", unmount and detach.
  - Run "bash _setup_cluster.sh test cluster_test.py" command on master to test the cluster.
  - Run "_run.sh" script that is responsible for launching the Piping sw on Spark.

"remove-cluster":
- Archive, transfer and extract a copy of this tool on Bastion.
- Execute  ansible cluster deprovision script with the file "cluster_vars.yaml".
- Remove the cluster-related files in the tool folder and cluster-related values in "config.yml" that are no longer needed.

"remove-all":
- "remove-cluster".
- Remove the Piping sw volume.
- Remove Bastion:
  - Terminate Bastion instance.
  - Deallocate the IP that was occupied by Bastion.
  - Remove the Bastion-related values in "config.yml" that are no longer needed.
- Remove CSC env setups:
  - Remove Bastion security group.
  - Remove cluster keypair.
  - Remove Bastion keypair.


