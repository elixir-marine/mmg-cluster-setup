
source Vars.sh

#. sh_script_modules/remove_cluster_everything.sh &&
#. sh_script_modules/remove_bastion.sh &&
#. sh_script_modules/remove_csc_setups.sh &&
#sleep 2

printf $SEPARATOR1"FIRST-TIME SETUP, STEP 1: PREPARE CSC ENVIRONMENT & BASTION"$SEPARATOR2
sleep 1
. sh_script_modules/prepare_csc_bastion.sh
sleep 0.5

printf $SEPARATOR1"FIRST-TIME SETUP, STEP 2: SETUP BASTION"$SEPARATOR2
sleep 1
. sh_script_modules/setup_bastion.sh
. sh_script_modules/setup_bastion.sh
sleep 0.5

printf $SEPARATOR1"FIRST-TIME SETUP, STEP 3: PREPARE CLUSTER"$SEPARATOR2
sleep 1
. sh_script_modules/prepare_cluster.sh
sleep 0.5

printf $SEPARATOR1"NEW CLUSTER, STEP 1: SETUP CLUSTER"$SEPARATOR2
sleep 1
. sh_script_modules/setup_cluster.sh
sleep 0.5

printf $SEPARATOR1"NEW CLUSTER, STEP 2: TEST CLUSTER"$SEPARATOR2
sleep 1
. sh_script_modules/test_cluster.sh
sleep 0.5

printf $SEPARATOR1"NEW CLUSTER, DONE!"$SEPARATOR2
read -n1 -r -p "Press any key to exit...`echo $'\n> '`" key


