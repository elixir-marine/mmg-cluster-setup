
source _init.sh

java -jar $SW_EXECUTABLE validate

sleep 2

if [ "$1" != "assembly" ]; then
    source _run_func_analysis.sh "$@"
else
    source _run_assembly.sh "${@:2}"
fi
