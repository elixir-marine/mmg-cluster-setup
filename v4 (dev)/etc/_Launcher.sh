
TEMP_FOLDER="temp"
LOGIN_FILE="login"
mkdir -p $TEMP_FOLDER

if [ ! -f $TEMP_FOLDER/$LOGIN_FILE ]; then
    USERNAME_INPUT="Please enter username: "
    while IFS= read -p "$USERNAME_INPUT" -r -s -n 1 char
    do
	if [[ $char == $'\0' ]]
	then
	    break
	fi
	USERNAME_INPUT=${char}
	USERNAME="${USERNAME}${char}"
    done
    echo $USERNAME > $TEMP_FOLDER/$LOGIN_FILE
    echo
    PASSWORD_INPUT="Please enter password: "
    while IFS= read -p "$PASSWORD_INPUT" -r -s -n 1 char
    do
	if [[ $char == $'\0' ]]
	then
	    break
	fi
	PASSWORD_INPUT='*'
	PASSWORD="${PASSWORD}${char}"
    done
    echo $PASSWORD >> $TEMP_FOLDER/$LOGIN_FILE
    echo
else
    USERNAME=$(sed '1q;d' $TEMP_FOLDER/$LOGIN_FILE)
    PASSWORD=$(sed '2q;d' $TEMP_FOLDER/$LOGIN_FILE)
fi

if [ -z "$SHELL" ]; then
    echo 'ERROR: $SHELL variable not set.'
    exit 1
fi

echo "Current shell: $SHELL"

java -jar Metapipe-cPouta.jar username=$USERNAME password=$PASSWORD

read -n1 -r -p "Press any key to close the window or exit..." key
echo
