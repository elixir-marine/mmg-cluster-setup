# mmg-cluster-setup
  
|  
  
Command-line tool for setting up processing software (META-pipe) on OpenStack (cPouta, pouta.csc.fi). Runs complex procedures with 1 or few commands.  
The tool was tested on (K)Ubuntu 12.04/14.04/16.04.  
Current META-pipe version used by the tool: v40 (executable) / v13 (dependencies).
  
|  
  
README structure:
- Requirements.
- What the tool can do.
- Current limitations / 2-be-done in the future.
- Structure of the tool folder "v4".
- Good 2 know before using the tool.
- Commands.
- Algorithm Details.
  
|  
  
|  
  

Requirements:
- Linux. Preferably (K)Ubuntu, v12.04 or higher.
- OpenStack username/password, OpenStack active project with enough available resources.
- Installed Java 8: "openjdk-8-jdk" for (K)Ubuntu, "java-1.8.0-openjdk" for CentOS.
- Firefox (for viewing Spark web UI).
  
|  
  
What the tool can do:
- Set up OpenStack environment.
- Prepare processing software (META-pipe) for being used by a cluster.
- Provision cluster, virtual disks, configure security and NFS for the cluster.
- Setup and configure Spark in standalone mode, run a spark test on the configured cluster.
- Prepare, validate and run processing software on the cluster.
- Deprovision existing cluster, cleanup everything.
- Vast amount of logging/debugging information displayed and saved into text files.
- Display information such as quota/overview, how much time it has taken to run a procedure, etc.
- For more info, see "Commands" and "Algorithm Details" below.
  
|  
  
Current limitations / 2-be-done in the future:
- The use of anti-affinity groups is disabled until it is implemented in Ansible 2.2.
- Only 1 bastion/cluster can exist per time.
- META-pipe assembly step is not available yet.
- When the processing software is launched on the cluster, the tool will print all its output, until the user presses Ctrl+C. To stop the sw, user must launch the tool again to run the stop command. And to continue watching the sw output after Ctrl+C, the user must stop and re-launch the sw.
  
|  
  
Structure of the tool folder "v4":
- "out": ready-to-use tool.
  - "config.yml": tool configuration file, to be set up by user.
  - "Launcher.desktop": tool launcher for Ubuntu and CentOS - double-click to run the tool.
  - "Metapipe-cPouta.jar": tool executable.
  - "logs": the folder where logs are stored. Logs' name contains date/time when the tool session was started.
  - "temp": temporary files created by the tool.
  - "pouta-ansible-cluster" folder: cluster provision/deprovision ansible script by CSC-IT-Center-for-Science, version of 06.03.2017, modified.
    - https://github.com/CSC-IT-Center-for-Science/pouta-ansible-cluster
    - https://github.com/CSC-IT-Center-for-Science/pouta-ansible-cluster/blob/feature/heterogenous_vm_support%2324/playbooks/hortonworks/README.md
  - "sw_files": Contains processing-software-related scripts; the following scripts are required:
    - "_init.sh": init necessary variables, is modified by the tool.
    - "_prepare.sh": meant to run processing software preparation routines (unpacking, placement, configuration).
    - "_run.sh": launches installed processing sw.
    - "_stop.sh": stops Spark processes, kills if they could not be stopped.
    - "_test.sh": validates installed sw.
  - "pouta-ansible-cluster.changed-files.txt": list of files in "pouta-ansible-cluster" folder which were modified and differ from the original CSC repo.
  - "_init.sh": init necessary variables, is modified by the tool.
  - "_disk.sh": a helper script for disk operations on the volumes attached to the VM where the script runs.
  - "_setup_cluster.sh": sets up Spark Standalone, to be executed on master.
  - "_disablePasswordAuth.sh": disables password authentication on the machine where the script is executed.
  - "cluster_test.py": test script executed on the cluster when the cluster is created and configured. Can be possibly replaced with own script.
  - "Launcher.sh" - launcher component.
  - "{clusterName}_firefox.sh": executable weblink shortcut that opens Spark web UI pages (4040 and 8080) in Firefox, is generated during cluster creation.
  - other files/folders: other tool components.
- other files/folders: source files.
  
|  
  
Good 2 know before using the tool:
- Make sure "config.yml" is configured correctly and according to the needs. Pay attention to the comments in the file. When the tool is launched, it runs config validation.
- How to run:
  - Method 1:
    - Double-click "Launcher.desktop".
    - If the launcher is run for the first time, it will prompt for OpenStack username and password, and remember them.
	- If the tool was launched with incorrect credentials, remove the file "temp/login" and re-run the launcher.
  - Method 2:
    - Open terminal in the tool folder, run "java -jar Metapipe-cPouta.jar".
    - The user will be prompted for OpenStack username and password.
  - Method 3:
    - Open terminal in the tool folder, run "java -jar Metapipe-cPouta.jar username=username password=password".
- When the tool is launched, the list of all implemented commands is shown.
- The tool has terminal-like autocomplete, press Tab when command is partially entered.
- Before running a complex operation, the tool validates whether the project has enough resources available. If yes, the operation continues. If no, it is cancelled. However not all resource information might be available via OS API, in this case the resource is ignored during the validation and the user should check it manually with help of OpenStack project web-pages. In the "overview" table of the tool, such resources are shown as "n/a".
- Output from each tool session is written to a separate log file. Logs can be quite big. If auto-delition of older logs was disabled in the config, it might be good to clean the logs folder manually sometimes.
- The duration of "create-*", "remove-*", "test", "sw-update" and "execute-*" is printed in the end when the process is finished.
  
|  
  
Commands:
- "help".
- "quit" and "exit".
- "overview":
  Shows table with info about total/used/available resources, and the resources amount required for 'create-*' commands.
- "create-env":
  Creates environment - required OS settings, bastion host, virtual volume for storing processing software.
- "create-cluster":
  Creates VMs, volumes and security settings for the cluster.
  Creates volumes for processing software and its temp files, to be NFS-shared.
  Sets up NFS, Spark Standalone, runs test script on the cluster.
  Prepares and valiates processing software.
- "create-all":
  "create-env" + "create-cluster".
- "test":
  Runs test script on the cluster, runs SW validation. Included into "create-cluster".
- "sw-launch":
  Launches the processing software, holds the session until the sw exists or Ctrl+C is pressed. After "sw-launch", "sw-stop" should be executed.
- "sw-stop":
  Stops Spark processes, kills if they were not stopped.
- "sw-update":
  Removes the contents of the SW-disk, re-downloads and prepares the sw. If cluster exists: to start using the updated SW, remove the existing cluster and create new.
- "remove-cluster":
  Removes the existing cluster.
- "remove-all":
  Removes the existing cluster + everything that was done by "create-env". A total clean-up.
- "remove-env":
  To be used if ssh-session to Bastion hangs during "remove-all". Removes bastion, OS setups, SW-disk; config.yml cleanup. Cluster deprovision skipped.
- "remove-create-cluster":
  "remove-cluster" + "create-cluster".
- "admin-add X.X.X.X" (1); "admin-add X.X.X.X, X.X.X.X, ..." (2); "admin-add X.X.X.X-X.X.X.X" (3); "admin-add X.X.X.X-X.X.X.X, X.X.X.X-X.X.X.X, ..." (4):
  Opens access to Spark cluster web UI for IP address (1), addresses (2), address range (3), address ranges (4), mix of addresses and ranges. To open access for everyone, run "ip-admin-add 0.0.0.0-255.255.255.255".
- "admin-remove X.X.X.X" (1); "admin-remove X.X.X.X, X.X.X.X, ..." (2); "admin-remove X.X.X.X-X.X.X.X" (3); "admin-remove X.X.X.X-X.X.X.X, X.X.X.X-X.X.X.X, ..." (4):
  Remove IP adress(es)/range(s) from admins.
- 'admin-list':
  List admin IPs and OpenStack CIDRs.
- "execute-bastion>>> command " (1), "execute-bastion>>> command ; command ; ... " (2):
  Execute a Bash command (1) or a single-line set of Bash commands (2) on Bastion. Both single and double quotes are allowed.
- "execute-master>>> command " (1), "execute-master>>> command ; command ; ... " (2):
  Execute a Bash command (1) or a single-line set of Bash commands (2) on Master. Both single and double quotes are allowed.
  
|  
  
|  
  

Algorithm Details (the variable names {X} are taken from config.yml):

"overview":
- Reload all resource stats/info.
- Pretty-print.

"create-env":
- Reload all resource stats/info, check whether required resources amount is available, confinue if yes.
- Start stopwatch.
- Bastion security setup if it was not previously done:
  - Create security group {bastionSecGroupName}.
  - Add security group rule: Ingress, port 22, protocol TCP, address 0.0.0.0/0.
  - Add security group rule: Ingress, port -, protocol ICMP, address 0.0.0.0/0. (made for being able to ping Bastion, will be removed in later versions)
- Create Bastion keypair {bastionKeyName}:
  - Check if the keypair exists, continue if no.
  - Generate keys in the .ssh folder if they don't exist, make them readable and writable for user only.
  - Register the public key on OpenStack.
- Create Bastion host:
  - If doesn't exist, create server with name {bastionMachineName}, keyname {bastionKeyName}, security groups {bastionSecGroupName} and "default", network ID {networkId}.
- Attach a public IP address to the Bastion.
- Transfer this tool to Bastion.
  - Put all tool components in an archive.
  - Transfer the archive to Bastion.
  - Exctact the archive on Bastion.
- Disable password authentication on Bastion.
- Set up Bastion:
  - Install necessary standard packages.
  - Install Ansible 2.3 and required python virtualenv modules.
- Create Cluster keypair {clusterKeyName}:
  - If exists, remove.
  - Generate new key pair in the "temp" folder.
  - Register the public key on OpenStack.
  - Transfer the key pair to the Bastion, place in ~/.ssh folder.
  - Set required read/write policies for the contents of the .ssh folder on Bastion.
- Modify .ssh/config on Bastion:
  - Host 192.168.1.* (taken from the Bastion private IP address), StrictHostKeyChecking no, IdentitiesOnly yes, IdentityFile ~/.ssh/{clusterKeyFileName}.
- Prepare processing software (META-pipe) and SW-disk:
  - Create the volume.
  - Attach to Bastion, partition, create a file system, detach.
  - Prepare processing software.
    - Attach disk to Bastion, mount.
    - Run the "sw_files/_prepare.sh" script with "sw-artifacts-prepare" flag, username and password for artifacts download:
      - Download and unpack artifacts.
      - Additional operations.
    - Unmount and detach.
- Update tool comporents.
- Stop stopwatch, print the time spent on the execution.

"create-cluster":
- Start stopwatch.
- Reload all resource stats/info, check whether required resources amount is available, confinue if yes.
- Update tool comporents.
  - Generate "cluster_vars.yaml" file in the "temp" folder, using "cluster_vars.yaml.template" and config.yml.
- Transfer this tool to Bastion.
- Execute cluster provision on Bastion:
  - Init virtualenv/ansible.
  - Run ansible cluster provision script with "cluster_vars.yaml".
    - Server group, volumes, VMs, volume attachments.
    - Master gets 2 additional volumes for processing software.
      - Main volume created from the SW-disk from "create-env", contains processing software.
      - Volume for TMP/history files, clean.
  - Run ansible cluster configuration script with "cluster_vars.yaml".
    - Processing software volumes on Master:
      - Main sw volume on master: partitioning and formatting skipped, attach only.
      - Main and TMP sw volumes on master: NFS-shared with worker VMs.
    - Configure all other necessary stuff.
- Open access to the cluster master for all "admins":
  - Get the IP used for the internet connection on the machine where the tool is running, using {ipCheck}.
  - Add this IP to the {ipAdmins} list.
  - For all IPs in the {ipAdmins} list, create a security group rule in the group {clusterName}-master with the parameters: Ingress, port 4040, protocol TCP.
  - For all IPs in the {ipAdmins} list, create a security group rule in the group {clusterName}-master with the parameters: Ingress, port 8080, protocol TCP.
- Generate Spark web UI executable shortcut.
- Launch the tool on Bastion.
- Running on Bastion:
  - If Java 8 is missing, install.
  - Transfer this tool to Master.
  - Launch "_setup_cluster.sh" script on Master:
    - Disable password auth.
      - Master itself.
      - Worker VMs.
    - Install Scala, Spark.
    - Register Spark slave nodes.
    - Configure Spark resource-related values.
  - Run "_setup_cluster.sh test cluster_test.py" on master to test the cluster.
  - Test cluster: start Spark, spark-submit "cluster_test.py", stop Spark.
  - Prepare processing software (META-pipe): "_prepare.sh" (mkdir/cp/ln/chmod minor stuff).
  - Test the processing software: "_test.sh", runs META-pipe validation.
- Stop stopwatch, print the time spent on the execution.

"create-all":
- Start stopwatch.
- Reload all resource stats/info, check whether required resources amount is available, confinue if yes.
- "create-env" (skipped start/stop stopwatch & resource stats/info reload).
- "create-cluster" (skipped start/stop stopwatch & resource stats/info reload).
- Stop stopwatch, print the time spent on the execution.

"test", "test-dev":
- Start stopwatch.
- Update tool comporents.
- (only "test-dev") Transfer this tool to Bastion.
- Launch the tool on Bastion.
- Running on Bastion:
  - (only "test-dev") Transfer this tool to Master.
  - Run "_stop.sh": stops Spark processes, kills if they could not be stopped.
  - Test cluster: start Spark, spark-submit "cluster_test.py", stop Spark.
  - Test the processing software: "_test.sh", runs META-pipe validation.
- Stop stopwatch, print the time spent on the execution.

"sw-launch", "sw-launch-dev":
- Update tool comporents.
- (only "sw-launch-dev") Transfer this tool to Bastion.
- Launch the tool on Bastion.
- Running on Bastion:
  - (only "sw-launch-dev") Transfer this tool to Master.
  - Run "_stop.sh": stops Spark processes, kills if they could not be stopped.
  - Run "_run.sh": launch processing software.
  - ...running until the sw exits or the user presses Ctrl+C.

"sw-stop", "sw-stop-dev":
- Update tool comporents.
- (only "sw-stop-dev") Transfer this tool to Bastion.
- Launch the tool on Bastion.
- Running on Bastion:
  - (only "sw-stop-dev") Transfer this tool to Master.
  - Run "_stop.sh": stops Spark processes, kills if they could not be stopped.

"sw-update":
- Start stopwatch.
- Update tool comporents.
- Transfer this tool to Bastion.
- Attach SW-disk to Bastion, mount.
- Run the "sw_files/_prepare.sh" script with "cleanup" flag: removes files from the disk.
- Run the "sw_files/_prepare.sh" script with "sw-artifacts-prepare" flag, username and password for artifacts download:
  - Download and unpack artifacts.
  - Additional operations.
- Unmount and detach SW-disk.
- Stop stopwatch, print the time spent on the execution.

"remove-cluster":
- Start stopwatch.
- Update tool comporents.
- Transfer this tool to Bastion.
- Run ansible cluster deprovision script with the file "cluster_vars.yaml".
- Remove the cluster-related files in the tool folder and cluster-related generated values in "config.yml".
- Stop stopwatch, print the time spent on the execution.

"remove-all":
- Start stopwatch.
- "remove-cluster" (skipped start/stop stopwatch).
- "remove-env" (skipped start/stop stopwatch).
- Stop stopwatch, print the time spent on the execution.

"remove-env":
- Start stopwatch.
- Remove SW-disk.
- Remove Bastion:
  - Terminate Bastion instance.
  - Deallocate the IP that was occupied by Bastion.
  - Remove the Bastion-related generated values in "config.yml".
- Remove CSC env setups:
  - Remove Bastion security group.
  - Remove cluster OpenStack keypair.
  - Remove Bastion OpenStack keypair.
  - Remove the CSC-Env-related generated values in "config.yml".
- Stop stopwatch, print the time spent on the execution.

"remove-create-cluster":
- Start stopwatch.
- "remove-cluster" (skipped start/stop stopwatch).
- "create-cluster" (skipped start/stop stopwatch).
- Stop stopwatch, print the time spent on the execution.


