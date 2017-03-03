#!/usr/bin/env bash

# Arg 1: Disk ID, Arg 2: operation, Arg 3: Disk Name

DISK_ID="$1"
DISK_NAME="$3"

while read SERIAL; do
    echo "Found device with ID: " $SERIAL
    SERIAL=${SERIAL#virtio-}
    if [[ $DISK_ID == *$SERIAL* ]]; then
        DEVICE=$(readlink -f /dev/disk/by-id/*$SERIAL);
        echo $DEVICE" "$SERIAL;
        break
    fi
done <<< "$(ls /dev/disk/by-id/)"

if [ -z $DEVICE ]; then
    echo "Disk not found! Check the correctness of the disk name in the argument, ensure the disk is attached to the VM."
    exit 0
fi

#unset DISK
#while read x; do
#    echo "Found (mounted or unmounted) partition: " $x
#    if [[ $x == *$DISK_NAME* ]]; then
#        DEVICE=$(echo "$x" | grep --color=NEVER -o '/dev/\w*');
#        break
#    fi
#done <<< "$(sudo blkid -c /dev/null)"
#if [ -z "$VAR" ]; then
#    echo "Disk not found! Check the correctness of the disk name in the argument, ensure the disk is attached to the VM."
#    exit 0
#fi

echo $DEVICE

if [ "$2" == "create" ]; then
    sudo parted -l
    sudo parted -s -a optimal $DEVICE mklabel gpt
    sudo parted -s -a optimal $DEVICE mkpart primary 0% 100%
    sudo mkfs.ext4 ${DEVICE}1
    sudo e2label ${DEVICE}1 $DISK_NAME
    #echo "LABEL=$DISK_NAME    /media/$DISK_NAME    ext4    defaults    0    0" | sudo tee --append /etc/fstab
    echo "Partition created."
    sudo parted -l | grep --color=NEVER '/dev/'
elif [ "$2" == "mount" ]; then
    sudo mkdir -p /media/$DISK_NAME
    sudo mount ${DEVICE}1 /media/$DISK_NAME
    echo "Partition mounted."
    df -aTh | grep --color=NEVER "/dev/v"
elif [ "$2" == "unmount" ]; then
    sudo umount ${DEVICE}1
    sudo umount /media/$DISK_NAME
    sudo rm -r /media/$DISK_NAME
    echo "Partition unmounted."
    df -aTh | grep --color=NEVER "/dev/v"
else
    echo "Unknown parameter"
    exit 0
fi
