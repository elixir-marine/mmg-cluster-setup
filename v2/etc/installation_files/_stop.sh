
source _init.sh

stop-all.sh

sleep 1

sudo kill $(ps aux | grep 'spark' | awk '{print $2}')
for name in "${WORKER_HOSTS[@]}"; do
    echo "$name"
    ssh -n -o StrictHostKeyChecking=no cloud-user@$name "sudo kill $(ps aux | grep 'spark' | awk '{print $2}')"
done