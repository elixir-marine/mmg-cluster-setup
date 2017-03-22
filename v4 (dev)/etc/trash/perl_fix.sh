
# Temporary solution for missing Perl module Data/Dumper.pm and Digest::MD5
#if [ "$1" != "sw-update" ]; then
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
#fi
