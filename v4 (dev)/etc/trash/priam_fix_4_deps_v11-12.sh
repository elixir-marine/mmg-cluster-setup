

# To use in "_prepare.sh"

# Temporary solution for Priam that crashes during the first job run
sudo mkdir $METAPIPE_DIR/metapipe/databases/priam/PRIAM_MAR15/PROFILES/LIBRARY
cd $METAPIPE_DIR/metapipe/databases/priam/PRIAM_MAR15/PROFILES
sudo chmod 777 -R .
unset FILELIST
declare -a FILELIST
for f in *; do
    if [ "$(echo "$f")" != "LIBRARY" ]; then
        FILELIST[${#FILELIST[@]}+1]="../"$(echo "$f");
    fi
done
printf "%s\n" "${FILELIST[@]}" > LIBRARY/profiles.list
cd LIBRARY
$METAPIPE_DIR/metapipe/tools/blast-legacy/bin/formatrpsdb -i profiles.list -o T -n PROFILE_EZ -t PRIAM_profiles_database
cd $METAPIPE_DIR

