package reductions

import org.scalameter.*

object ParallelCountChangeRunner:

  @volatile var seqResult = 0

  @volatile var parResult = 0

  val standardConfig = config(
    Key.exec.minWarmupRuns := 20,
    Key.exec.maxWarmupRuns := 40,
    Key.exec.benchRuns := 80,
    Key.verbose := false
  ) withWarmer(Warmer.Default())

  def main(args: Array[String]): Unit =
    val amount = 250
    val coins = List(1, 2, 5, 10, 20, 50)
    val seqtime = standardConfig measure {
      seqResult = ParallelCountChange.countChange(amount, coins)
    }
    println(s"sequential result = $seqResult")
    println(s"sequential count time: $seqtime")

    def measureParallelCountChange(threshold: => ParallelCountChange.Threshold): Unit = try
      val fjtime = standardConfig measure {
        parResult = ParallelCountChange.parCountChange(amount, coins, threshold)
      }
      println(s"parallel result = $parResult")
      println(s"parallel count time: $fjtime")
      println(s"speedup: ${seqtime.value / fjtime.value}")
    catch
      case e: NotImplementedError =>
        println("Not implemented.")

    println("\n# Using moneyThreshold\n")
    measureParallelCountChange(ParallelCountChange.moneyThreshold(amount))
    println("\n# Using totalCoinsThreshold\n")
    measureParallelCountChange(ParallelCountChange.totalCoinsThreshold(coins.length))
    println("\n# Using combinedThreshold\n")
    measureParallelCountChange(ParallelCountChange.combinedThreshold(amount, coins))

object ParallelCountChange extends ParallelCountChangeInterface:

  /** Returns the number of ways change can be made from the specified list of
   *  coins for the specified amount of money.
   */
  def countChange(money: Int, coins: List[Int]): Int =
    // ???
    // could improve this by using helper method, cache...
    // countChangeCache(money, coins, cache: List.fill(coins.length - 1).(0))
    // var numberWay = 0
    if (money == 0)
      1
    else if (coins.isEmpty | money < 0 | (money > 0 && coins.length == 0)) 
      0
    else
      countChange(money - coins.head, coins) + 
      countChange(money, coins.tail)

  type Threshold = (Int, List[Int]) => Boolean

  /** In parallel, counts the number of ways change can be made from the
   *  specified list of coins for the specified amount of money.
   */
  def parCountChange(money: Int, coins: List[Int], threshold: Threshold): Int =
    // ???
    var th = threshold(money, coins)
    if (money == 0)
      1
    else if (coins.isEmpty | money < 0 | (money > 0 && coins.length == 0)) 
      0
    else if (th)
      countChange(money, coins)
    else
      val p = parallel(parCountChange(money - coins.head, coins, threshold), 
      parCountChange(money, coins.tail, threshold))
      p._1 + p._2
      // val (p1,p2) = parallel(parCountChange(money - coins.head, coins, th), 
      // parCountChange(money, coins.tail, th))
      // p1 + p2

  /** Threshold heuristic based on the starting money. */
  def moneyThreshold(startingMoney: Int): Threshold =
    // ???, not my own code, since where's the threshold parameter?, only startingMoney doesn't make sense...
    // don't know enough scala 'grammar' to implement it,
    (money, _) => {
      var eval = money <= startingMoney * 2 / 3
      eval
      // money <= eval
    } 

    

  /** Threshold heuristic based on the total number of initial coins. */
  def totalCoinsThreshold(totalCoins: Int): Threshold =
    // ???
    (_, coins) => {
      coins.length <= totalCoins * 2 / 3
    }


  /** Threshold heuristic based on the starting money and the initial list of coins. */
  def combinedThreshold(startingMoney: Int, allCoins: List[Int]): Threshold =
    // ???
    (money, coins) => {
      money * coins.length <= startingMoney * allCoins.length / 2
    }
