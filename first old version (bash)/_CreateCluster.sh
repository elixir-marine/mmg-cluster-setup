
source Vars.sh

#printf $SEPARATOR1"REMOVING CLUSTER"$SEPARATOR2
#sleep 1.5
#source sh_script_modules/remove_cluster_everything.sh
#sleep 0.5

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