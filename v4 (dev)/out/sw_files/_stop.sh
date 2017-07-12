
source _init.sh

source $SPARK_HOME/sbin/stop-all.sh

sleep 3

# /export/sw_main/sw/metapipe/tools/blast/bin/
# /export/sw_main/sw/metapipe/tools/blast-legacy/bin/
# /export/sw_main/sw/metapipe/tools/interpro/hmmer/
# /export/sw_main/sw/metapipe/tools/interpro/bin/
# /export/sw_main/sw/metapipe/tools/megahit/
# /export/sw_main/sw/metapipe/tools/mga/
# /export/sw_main/sw/metapipe/tools/priam/
# /export/sw_main/sw/metapipe/tools/seqprep/
# /export/sw_main/sw/metapipe/tools/trimmomatic/

# find /export/sw_main/sw/metapipe/tools/ -perm -111 -type f ! -name "*.*" -print0 | xargs -0 basename
# find /export/sw_main/sw/metapipe/tools/ -perm -111 -type f ! -name "*.*" -exec basename {} \;

#sudo kill -9 $(jps | grep "Master" | cut -d " " -f 1) 2> /dev/null
#sudo kill -9 $(ps aux | grep [^]]spark | awk '{print $2}')

P_TO_KILL="$(jps | grep 'SparkSubmit' | cut -d ' ' -f 1)"
if [ ! -z "$P_TO_KILL" ]; then echo $(echo 'Killing process: java/SparkSubmit |' "$P_TO_KILL" | tr '\n' ' '); sudo kill -9 $P_TO_KILL; fi
P_TO_KILL="$(jps | grep 'Master' | cut -d ' ' -f 1)"
if [ ! -z "$P_TO_KILL" ]; then echo $(echo 'Killing process: java/Master |' "$P_TO_KILL" | tr '\n' ' '); sudo kill -9 $P_TO_KILL; fi
P_TO_KILL="$(ps aux | grep [^]]'spark' | grep -v grep | awk '{print $2}')"
if [ ! -z "$P_TO_KILL" ]; then echo $(echo 'Killing process: spark |' "$P_TO_KILL" | tr '\n' ' '); sudo kill -9 $P_TO_KILL; fi

TO_KILL=$(find /export/sw_main/sw/metapipe/tools/ -perm -111 -type f ! -name "*.*" ! -path "*/ss" -exec basename {} \;)
for i in ${TO_KILL[@]}
do
	#echo ========================== $i
	P_TO_KILL="$(ps aux | grep [^]]$i | grep -v grep | awk '{print $2}')"
    if [ ! -z "$P_TO_KILL" ]; then echo $(echo 'Killing process: ' $i '|' "$P_TO_KILL" | tr '\n' ' '); sudo kill -9 $P_TO_KILL; fi
done

#for name in "${WORKER_HOSTS[@]}"; do
#    echo "$name"
#    ssh -n -o StrictHostKeyChecking=no cloud-user@$name "
#    TO_KILL=('${TO_KILL[*]}')
#    P_TO_KILL=$(jps | grep 'SparkSubmit' | cut -d ' ' -f 1)
#    if [ ! -z \$P_TO_KILL ]; then echo 'Killing process: java/SparkSubmit |' \"\$P_TO_KILL\" | tr '\n' ' '; echo ''; sudo kill -9 \$P_TO_KILL; fi
#    P_TO_KILL=$(jps | grep 'Worker' | cut -d ' ' -f 1)
#    if [ ! -z \$P_TO_KILL ]; then echo 'Killing process: java/Worker |' \"\$P_TO_KILL\" | tr '\n' ' '; echo ''; sudo kill -9 \$P_TO_KILL; fi
#    P_TO_KILL=$(ps aux | grep [^]]'spark' | grep -v grep | awk '{print $2}')
#    if [ ! -z \$P_TO_KILL ]; then echo 'Killing process: spark |' \"\$P_TO_KILL\" | tr '\n' ' '; echo ''; sudo kill -9 \$P_TO_KILL; fi
#    for i in \${TO_KILL[@]}
#    do
#        P_TO_KILL=\"\$(ps aux | grep [^]]\$i | grep -v grep | awk '{print \$2}')\"
#        if [ ! -z \"\$P_TO_KILL\" ]; then echo 'Killing process: ' \$i '|' \"\$P_TO_KILL\" | tr '\n' ' '; echo ''; sudo kill -9 \$P_TO_KILL; fi
#    done
#    " &
#done
#wait

echo ${WORKER_HOSTS[@]} | tr ' ' '\n' | xargs -n 1 -P ${#WORKER_HOSTS[*]} -i ssh -n -o StrictHostKeyChecking=no cloud-user@{} "
    #echo \$(hostname)
    TO_KILL=('${TO_KILL[*]}')
    P_TO_KILL=\"\$(jps | grep 'SparkSubmit' | cut -d ' ' -f 1)\"
    if [ ! -z \"\$P_TO_KILL\" ]; then echo \$(echo \$(hostname) '/ Killing process: java/SparkSubmit |' \"\$P_TO_KILL\" | tr '\n' ' '); sudo kill -9 \$P_TO_KILL; fi
    P_TO_KILL=\"\$(jps | grep 'CoarseGrainedExecutorBackend' | cut -d ' ' -f 1)\"
    if [ ! -z \"\$P_TO_KILL\" ]; then echo \$(echo \$(hostname) '/ Killing process: java/CoarseGrainedExecutorBackend |' \"\$P_TO_KILL\" | tr '\n' ' '); sudo kill -9 \$P_TO_KILL; fi
    P_TO_KILL=\"\$(jps | grep 'Worker' | cut -d ' ' -f 1)\"
    if [ ! -z \"\$P_TO_KILL\" ]; then echo \$(echo \$(hostname) '/ Killing process: java/Worker |' \"\$P_TO_KILL\" | tr '\n' ' '); sudo kill -9 \$P_TO_KILL; fi
    P_TO_KILL=\"\$(ps aux | grep [^]]'spark' | grep -v grep | awk '{print \$2}')\"
    if [ ! -z \"\$P_TO_KILL\" ]; then echo \$(echo \$(hostname) '/ Killing process: spark |' \"\$P_TO_KILL\" | tr '\n' ' '); sudo kill -9 \$P_TO_KILL; fi
    for i in \${TO_KILL[@]}
    do
        P_TO_KILL=\"\$(ps aux | grep [^]]\$i | grep -v grep | awk '{print \$2}')\"
        if [ ! -z \"\$P_TO_KILL\" ]; then echo \$(echo \$(hostname) '/ Killing process: ' \$i '|' \"\$P_TO_KILL\" | tr '\n' ' '); sudo kill -9 \$P_TO_KILL; fi
    done
    "

sudo rm -r -f $SPARK_HOME/work/*
