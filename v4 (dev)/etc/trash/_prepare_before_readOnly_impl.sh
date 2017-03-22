
source _init.sh

if [ "$1" == "sw-disk-prepare" ]; then
    # do the download/unpack/rename stuff
    return
fi

sudo chmod 777 $METAPIPE_DIR
cd $METAPIPE_DIR

if [ "$1" == "sw-update" ]; then
    sudo rm -r metapipe
    sudo rm -r $METAPIPE_DIR/.metapipe
fi
if [ "$1" != "skip-unpack" ]; then
    sudo tar xvf /media/$SWDISK_NAME/$METAPIPE_ON_SWDISK_DIR_NAME/metapipe-dependencies.tar.gz -C $METAPIPE_DIR --keep-old-files
    sudo mv package/dist .
    sudo mv dist metapipe
fi
sudo cp /media/$SWDISK_NAME/$METAPIPE_ON_SWDISK_DIR_NAME/*.sh $METAPIPE_DIR
#sudo cp /media/$SWDISK_NAME/$METAPIPE_ON_SWDISK_DIR_NAME/conf.json $METAPIPE_DIR
sudo cp /media/$SWDISK_NAME/$METAPIPE_ON_SWDISK_DIR_NAME/$METAPIPE_EXECUTABLE $METAPIPE_DIR

sudo rm conf.json
echo "{
  \"metapipeHome\": \"$METAPIPE_DIR/metapipe\",
  \"metapipeTemp\": \"$METAPIPE_DIR/metapipe-tmp\"
}" > conf.json
cat conf.json
sudo cp conf.json $METAPIPE_DIR/metapipe
sudo mkdir $METAPIPE_DIR/.metapipe
sudo cp conf.json $METAPIPE_DIR/.metapipe
if [ "$1" != "sw-update" ]; then
    sudo mkdir $METAPIPE_DIR/metapipe-tmp
fi
sudo chmod 777 -R $METAPIPE_DIR
sudo rm /home/cloud-user/.metapipe
sudo ln -s $METAPIPE_DIR/.metapipe /home/cloud-user/

# Temporary solution for missing Perl module Data/Dumper.pm and Digest::MD5
if [ "$1" != "sw-update" ]; then
    sudo yum -y install perl-CPAN
    sudo yum -y install perl-Digest-MD5
    curl -L https://cpanmin.us | perl - --sudo App::cpanminus
    cpanm Digest::MD5
    for name in "${WORKER_HOSTS[@]}"; do
        echo "$name"
        ssh -t -o StrictHostKeyChecking=no cloud-user@$name '
        sudo yum -y install perl-CPAN
        sudo yum -y install perl-Digest-MD5
        curl -L https://cpanmin.us | perl - --sudo App::cpanminus
        cpanm Digest::MD5
        '
    done
fi

#>| $METAPIPE_DIR/metapipe-tmp/assembly_running
#for name in "${WORKER_HOSTS[@]}"; do
#    echo "0" >> $METAPIPE_DIR/metapipe-tmp/assembly_running
#done
#cat $METAPIPE_DIR/metapipe-tmp/assembly_running

if [ "$1" != "sw-update" ]; then
    echo "METAPIPE_DIR=$METAPIPE_DIR" >> $SPARK_HOME/conf/spark-env.sh
fi
