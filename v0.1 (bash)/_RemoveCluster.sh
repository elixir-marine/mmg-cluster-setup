
source Vars.sh

printf $SEPARATOR1"REMOVING CLUSTER"$SEPARATOR2
source sh_script_modules/remove_cluster_everything.sh

printf $SEPARATOR1"DONE!"$SEPARATOR2
read -n1 -r -p "Press any key to exit...`echo $'\n> '`" key