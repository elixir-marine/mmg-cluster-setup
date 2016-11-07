
source _init.sh
sudo chmod 777 $METAPIPE_DIR
cd $METAPIPE_DIR

DISK_NAME="$1"

if [ "$2" != "skip-unpack" ]; then
    sudo tar xvf /media/$DISK_NAME/sw-packed/metapipe-deps-current.tar.gz -C $METAPIPE_DIR --keep-old-files
fi
sudo cp /media/$DISK_NAME/sw-packed/*.sh $METAPIPE_DIR
sudo cp /media/$DISK_NAME/sw-packed/conf.json $METAPIPE_DIR
sudo cp /media/$DISK_NAME/sw-packed/blast-2.2.19-x64-linux.tar.gz $METAPIPE_DIR
sudo cp /media/$DISK_NAME/sw-packed/ncbi-blast-2.4.0+-2.x86_64.rpm $METAPIPE_DIR
sudo cp /media/$DISK_NAME/sw-packed/workflow-assembly-0.1-SNAPSHOT.jar $METAPIPE_DIR

sudo mkdir $METAPIPE_DIR/.metapipe
sudo mkdir $METAPIPE_DIR/metapipe-tmp
sudo rm conf.json
echo "{
  \"metapipeHome\": \"$METAPIPE_DIR/metapipe\",
  \"metapipeTemp\": \"$METAPIPE_DIR/metapipe-tmp\"
}" > conf.json
cat conf.json
sudo cp conf.json $METAPIPE_DIR/.metapipe
sudo cp conf.json $METAPIPE_DIR/metapipe
sudo chmod 777 -R $METAPIPE_DIR
sudo rm /home/cloud-user/.metapipe
sudo ln -s $METAPIPE_DIR/.metapipe /home/cloud-user/

# Temporary solution for Interpro (the launcher is hardcoded for Stallo)
sed -i '$ d' $METAPIPE_DIR/metapipe/bin/interpro
echo "$METAPIPE_DIR/metapipe/tools/interpro/interproscan.sh" '"$@"' | sudo tee --append $METAPIPE_DIR/metapipe/bin/interpro
cat $METAPIPE_DIR/metapipe/bin/interpro

# Temporary solution for Blast Legacy (since not in Metapipe yet)
sudo tar xf $METAPIPE_DIR/blast-2.2.19-x64-linux.tar.gz -C $METAPIPE_DIR/metapipe/tools/ --keep-old-files

# Temporary solution for Blast+ (since not in Metapipe yet)
sudo yum -y localinstall $METAPIPE_DIR/ncbi-blast-2.4.0+-2.x86_64.rpm --nogpgcheck
for name in "${WORKER_HOSTS[@]}"; do
    echo "$name"
    ssh -n -o StrictHostKeyChecking=no cloud-user@$name "
    sudo yum -y localinstall $METAPIPE_DIR/ncbi-blast-2.4.0+-2.x86_64.rpm --nogpgcheck"
done

>| $METAPIPE_DIR/metapipe-tmp/assembly_running
for name in "${WORKER_HOSTS[@]}"; do
    echo "0" >> $METAPIPE_DIR/metapipe-tmp/assembly_running
done
cat $METAPIPE_DIR/metapipe-tmp/assembly_running

echo "METAPIPE_DIR=$METAPIPE_DIR" >> $SPARK_HOME/conf/spark-env.sh
echo 'DEPENDENCIES_PATH=$METAPIPE_DIR/metapipe/tools/blast-2.2.19/bin:$METAPIPE_DIR/metapipe/tools/interproscan-5.18-57.0:$METAPIPE_DIR/metapipe/bin' >> $SPARK_HOME/conf/spark-env.sh
echo 'export PATH=$DEPENDENCIES_PATH:$PATH' >> $SPARK_HOME/conf/spark-env.sh

# sudo kill $(ps aux | grep "spark" | awk '{print $2}')
