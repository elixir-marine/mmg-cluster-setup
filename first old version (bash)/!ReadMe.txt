

Script for setting up and taking down the Metapipe execution environment on cPouta.


Requirements:
- Your workstation is running (K)Ubuntu 14.04 or higher, or CentOS 6.5 or higher. The script was not tested on the older versions of these OS, so it might work or might not. Not tested on other Linux distributions.
- You have username and password for Pouta CSC portal, can login and have access to the Metapipe project on the portal.
- Internet.


The current version of the script was tested on (K)Ubuntu 14.04.
Testing of the script on CentOS and other versions of (K)Ubuntu is the next step.


Temporary limitations/issues:
- Currently, full potential of cPouta IO flavors is not used.
- Only 1 cluster can exist per time.


Usage:
  A. Creating everything from scratch:
    1. Open Terminal in this folder and run "bash _Prepare.sh".
    2. Open "Vars.sh":
      - Check the correctness of the parameters in the first block by opening Pouta CSC portal and comparing them.
      - Can change the parameters in the second block according to your wishes.
      - In other words follow the #comments.
    3. Open Terminal in this folder and run "bash _CreateAll.sh".
      - This script is to be used when setting up everything from scratch.
      - It creates and sets all required Pouta CSC parameters and settings, creates bastion host, creates cluster with Ambari/Spark/Hadoop and runs test on the cluster.
      - Monitor the execution process.
      - You will be asked to enter your Pouta CSC password several times during script execution.
      - You will see some error messages, some of them will even appear repeatedly in a row, this is normal, don't abort the script, they are supposed to show up on the screen.
      - Wait until the end of execution.
  B. Remove only cluster:
    1. You want to remove only the existing cluster, keep all the rest for future cluster creation and save some time.
    2. Open Terminal in this folder and run "bash _RemoveCluster.sh".
    3. Do A.2. if needed.
  C. Create only cluster:
    1. You want to create a new cluster after the above "Remove only cluster".
    2. Do A.2. if needed.
    3. Open Terminal in this folder and run "bash _CreateCluster.sh". The process will be similar to A.3., but shorter.
  D. Remove everything:
    1. You want to remove everything you have done in A. - cluster, bastion, and all the setups, etc.
    2. Open Terminal in this folder and run "bash _RemoveAll.sh".

Approximate "_CreateAll" timing:
- Everything before cluster creation: ~5-9 min.
- Cluster creation and testing, or just the "_CreateCluster" part: ~14-17 min. May be longer for bigger number of nodes.
Approximate "_RemoveAll" timing:
- Cluster deletion, or just the "_RemoveCluster" part: ~4-6 min. May be longer for bigger number of nodes.
- Delition of bastion and other setups: ~1 min.


Future work:
- Test the script on CentOS and other versions of (K)Ubuntu.
- Add execution abortion where necessary but missing.
- Update the Ansible part and modify the Bash part, to make the script use full potential of cPouta IO flavors.
- Find the way to remember cPouta password and eliminate the need to enter in multiple times during execution.
- Add creation of master and node images on the "first" run (CreateAll) and usage of them later (CreateCluster), possibly make 1-2 new "_"-scripts.
- Implement 2 new "_"-scripts for scaling up/down during cluster lifetime.
- Consider re-implementation of some Bash parts (/sh_script_modules) in Ansible or Java.
- Add support of multiple clusters existing in the same time.


General algorithm: (text to be added)



