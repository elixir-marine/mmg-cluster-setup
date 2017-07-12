

source _init.sh

if [ "$1" == "cleanup" ]; then
    cd /media/$SW_MAIN_DIR
    echo "Cleaning up..."
    sudo rm -r $SW_FILES_DIR_NAME
    sudo rm -r -f *                            # new
    return
    echo "THIS SHOULD NOT BE PRINTED!"
fi

if [ "$1" == "sw-artifacts-prepare" ]; then
    ARTIFACTS_USERNAME="$2"
    ARTIFACTS_PASSWORD="$3"
    cd /media/$SW_MAIN_DIR
    sudo mkdir -p $SW_FILES_DIR_NAME
    cd $SW_FILES_DIR_NAME
    sudo chmod 777 .
    curl -O -u $ARTIFACTS_USERNAME:$ARTIFACTS_PASSWORD $ARTIFACTS_FILES_EXEC
    if [ ! -d "metapipe" ]; then
        curl -u $ARTIFACTS_USERNAME:$ARTIFACTS_PASSWORD $ARTIFACTS_FILES_DEPS_ARC | tar xv -C .
        # Alternative: tar xvz< <(curl -u $ARTIFACTS_USERNAME:$ARTIFACTS_PASSWORD $ARTIFACTS_FILES_DEPS_ARC)
        sudo mv package/dist ./metapipe
        sudo rmdir package
    else
        echo "Deps directory 'metapipe' exists, skipping downloading/unpacking of deps."
    fi
    echo "{
      \"metapipeHome\": \"/$SW_PARENT_DIR/$SW_MAIN_DIR/$SW_FILES_DIR_NAME/metapipe\",
      \"metapipeTemp\": \"/$SW_PARENT_DIR/$SW_TMP_DIR/metapipe-tmp\"
}" > metapipe/conf.json
    cat metapipe/conf.json
    sudo mkdir .metapipe                        # new
    sudo cp metapipe/conf.json .metapipe        # new
    echo "Waiting..."
    sudo chmod 777 -R .
    return
    echo "THIS SHOULD NOT BE PRINTED!"
fi

# Here we must be already in /$SW_PARENT_DIR/$SW_MAIN_DIR/$METAPIPE_MAIN_DIR

#sudo mkdir .metapipe
#sudo cp metapipe/conf.json .metapipe
#sudo chmod 777 -R .metapipe
sudo ln -s $(pwd)/.metapipe /home/cloud-user/.metapipe

cd ../../$SW_TMP_DIR
sudo mkdir metapipe-tmp
sudo chmod 777 -R .

#echo "SW_MAIN_DIR=$SW_MAIN_DIR" >> $SPARK_HOME/conf/spark-env.sh

# Temporary solution for missing Perl module "Data/Dumper.pm" and "Digest::MD5":
# (moved to pouta-ansible-cluster script, in "/roles/base/tasks/main.yml")

#sudo yum install -y python-pip
#sudo pip install pssh

