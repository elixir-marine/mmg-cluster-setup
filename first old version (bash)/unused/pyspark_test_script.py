
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
while True:
  try:
    nums = sc.parallelize(xrange(number_of_nums))
    break
  except NameError:
    pass
# Compute the number of primes in the RDD
print "Number of primes in range from 0 to %d is: %d." % (number_of_nums, nums.filter(isprime).count())
