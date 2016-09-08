
# 2 SLAVES; 3-7 min.

source Vars.sh

printf $SEPARATOR1"REMOVING CLUSTER"$SEPARATOR2
source sh_script_modules/remove_cluster_everything.sh
sleep 0.5

printf $SEPARATOR1"REMOVING BASTION"$SEPARATOR2
sleep 1
source sh_script_modules/remove_bastion.sh
sleep 0.5

printf $SEPARATOR1"REMOVING CSC SETUPS"$SEPARATOR2
sleep 1
source sh_script_modules/remove_csc_setups.sh
sleep 0.5

printf $SEPARATOR1"DONE!"$SEPARATOR2
read -n1 -r -p "Press any key to exit...`echo $'\n> '`" key