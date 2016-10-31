
source ~/_init.sh

#METAPIPE_DIR=/data/metapipe
METAPIPE_DIR=/export/share # = SW_DIR
DEPENDENCIES_PATH=$METAPIPE_DIR/metapipe/tools/blast-2.2.19/bin:$METAPIPE_DIR/metapipe/tools/interproscan-5.18-57.0:$METAPIPE_DIR/metapipe/bin
export PATH=$DEPENDENCIES_PATH:$PATH
