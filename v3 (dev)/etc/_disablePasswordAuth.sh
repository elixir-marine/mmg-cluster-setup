
sudo bash -c 'grep -q "ChallengeResponseAuthentication" /etc/ssh/sshd_config && sed -i "/^[^#]*ChallengeResponseAuthentication[[:space:]]yes.*/c\ChallengeResponseAuthentication no" /etc/ssh/sshd_config || echo "ChallengeResponseAuthentication no" >> /etc/ssh/sshd_config'

sudo bash -c 'grep -q "^[^#]*PasswordAuthentication" /etc/ssh/sshd_config && sed -i "/^[^#]*PasswordAuthentication[[:space:]]yes/c\PasswordAuthentication no" /etc/ssh/sshd_config || echo "PasswordAuthentication no" >> /etc/ssh/sshd_config'

#sudo bash -c 'grep -q "^[^#]*UsePAM" /etc/ssh/sshd_config && sed -i "/^[^#]*UsePAM[[:space:]]yes/c\UsePAM no" /etc/ssh/sshd_config || echo "UsePAM no" >> /etc/ssh/sshd_config'

sudo systemctl restart sshd