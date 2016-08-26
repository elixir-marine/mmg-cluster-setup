
# https://districtdatalabs.silvrback.com/getting-started-with-spark-in-python
TEST_SCRIPT='
def isprime(n):
    # make sure n is a positive integer
    n = abs(int(n))
    # 0 and 1 are not primes
    if n < 2:
        return False
    # 2 is the only even prime number
    if n == 2:
        return True
    # all other even numbers are not primes
    if not n & 1:
        return False
    # range starts with 3 and only needs to go up the square root of n
    # for all odd numbers
    for x in range(3, int(n**0.5)+1, 2):
        if n % x == 0:
            return False
    return True

# Range from 0 to ... 10,000,000
number_of_nums = 10000000
# Create an RDD of numbers from 0 to 10,000,000
nums = sc.parallelize(xrange(number_of_nums))
# Compute the number of primes in the RDD
print "============================================================\nNumber of primes in range from 0 to %d is: %d.\n============================================================" % (number_of_nums, nums.filter(isprime).count())
'

ssh -o StrictHostKeyChecking=no cloud-user@$BASTION_IP " cd ~ "
while [ $? -ne 0 ]; do
  sleep 1
  ssh cloud-user@$BASTION_IP " cd ~ "
done

MESSAGE_TEST_MASTER="RUNNING TEST SCRIPT ON THE MASTER MACHINE"
MESSAGE_TEST_CLUSTER="RUNNING TEST SCRIPT ON THE WHOLE CLUSTER"

ssh cloud-user@$BASTION_IP bash -c "'
cd ~ && source $OPENRC_FILE && nova image-list
ssh -o StrictHostKeyChecking=no -t -t cloud-user@$MASTER_PRIVATE_IP << EOSSH

echo "$MESSAGE_TEST_MASTER"
sleep 3
sh /usr/hdp/current/spark-client/bin/pyspark
printf $TEST_SCRIPT
exit()

sleep 1

echo "$MESSAGE_TEST_CLUSTER"
sleep 3
sh /usr/hdp/current/spark-client/bin/pyspark --master yarn
printf $TEST_SCRIPT
exit()

logout
EOSSH
'"




