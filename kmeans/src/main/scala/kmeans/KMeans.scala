package kmeans

import scala.annotation.tailrec
import scala.collection.{Map, Seq, mutable}
import scala.collection.parallel.CollectionConverters.*
import scala.collection.parallel.{ForkJoinTaskSupport, ParMap, ParSeq}
import scala.util.Random
import org.scalameter.*
import java.util.concurrent.ForkJoinPool
import scala.language.postfixOps

/** Describes one point in three-dimensional space.
 *
 *  Note: deliberately uses reference equality.
 */
class Point(val x: Double, val y: Double, val z: Double):
  private def square(v: Double): Double = v * v
  def squareDistance(that: Point): Double =
    square(that.x - x)  + square(that.y - y) + square(that.z - z)
  private def round(v: Double): Double = (v * 100).toInt / 100.0
  override def toString = s"(${round(x)}, ${round(y)}, ${round(z)})"

class KMeans extends KMeansInterface:

  def generatePoints(k: Int, num: Int): ParSeq[Point] =
    val randx = Random(1)
    val randy = Random(3)
    val randz = Random(5)
    (0 until num)
      .map({ i =>
        val x = ((i + 1) % k) * 1.0 / k + randx.nextDouble() * 0.5
        val y = ((i + 5) % k) * 1.0 / k + randy.nextDouble() * 0.5
        val z = ((i + 7) % k) * 1.0 / k + randz.nextDouble() * 0.5
        Point(x, y, z)
      }).to(mutable.ArrayBuffer).par

  def initializeMeans(k: Int, points: ParSeq[Point]): ParSeq[Point] =
    val rand = Random(7)
    (0 until k).map(_ => points(rand.nextInt(points.length))).to(mutable.ArrayBuffer).par

  def findClosest(p: Point, means: IterableOnce[Point]): Point =
    val it = means.iterator
    assert(it.nonEmpty)
    var closest = it.next()
    var minDistance = p.squareDistance(closest)
    while it.hasNext do
      val point = it.next()
      val distance = p.squareDistance(point)
      if distance < minDistance then
        minDistance = distance
        closest = point
    closest

  // Test description: 'classify' should work for empty 'points' and 
  // 'means' == ParSeq(Point(1,1,1))(kmeans.KMeansSuite)
  def classify(points: ParSeq[Point], means: ParSeq[Point]): ParMap[Point, ParSeq[Point]] =
    // ???
    // need 'support' here...not mine
    val intermidAffectedByImperativeThingking0 = 
    // if (points.isEmpty) then ParMap(Point)
    // else
    points.par
    .groupBy(findClosest(_, means))

    means.par.map(mean => 
      if (intermidAffectedByImperativeThingking0(mean).isEmpty) 
        mean -> ParSeq()
      else
        mean -> intermidAffectedByImperativeThingking0(mean)
    ).toMap
    
    // val pointsMeanMap = points.par.groupBy(findClosest(_, means))
    // // So iterate over means get (empty) list and return map
    // means.par.map(mean => mean -> pointsMeanMap.getOrElse(mean, ParSeq())).toMap

  def findAverage(oldMean: Point, points: ParSeq[Point]): Point = if points.isEmpty then oldMean else
    var x = 0.0
    var y = 0.0
    var z = 0.0
    points.seq.foreach { p =>
      x += p.x
      y += p.y
      z += p.z
    }
    Point(x / points.length, y / points.length, z / points.length)

  def update(classified: ParMap[Point, ParSeq[Point]], oldMeans: ParSeq[Point]): ParSeq[Point] =
    // ???
    oldMeans.par
    .map(p => findAverage(p, classified(p)))
    // classified
    // .map((k,v) => findAverage(k,v))

  def converged(eta: Double, oldMeans: ParSeq[Point], newMeans: ParSeq[Point]): Boolean =
    // ???
    (oldMeans zip newMeans).forall(
      (oldMean, newMean) => oldMean.squareDistance(newMean) <= eta 
    )

    // var i = 0
    // if (oldMeans.length != newMeans.length) 
    //   false
    // else
    //   while (i < oldMeans.length && Math.abs(oldMeans(i).squareDistance(newMeans(i))) <= eta)
    //     i += 1
    // if (i == oldMeans.length - 1) true else false      

  @tailrec
  final def kMeans(points: ParSeq[Point], means: ParSeq[Point], eta: Double): ParSeq[Point] =
    // if (???) kMeans(???, ???, ???) else ??? // your implementation need to be tail recursive
    var newMeans = update(classify(points, means), means)
    if !converged(eta, means, newMeans) then
      kMeans(points, newMeans, eta)
    else
      newMeans


// /** Describes one point in three-dimensional space.
//  *
//  *  Note: deliberately uses reference equality.
//  */
// class Point(val x: Double, val y: Double, val z: Double):
//   private def square(v: Double): Double = v * v
//   def squareDistance(that: Point): Double =
//     square(that.x - x)  + square(that.y - y) + square(that.z - z)
//   private def round(v: Double): Double = (v * 100).toInt / 100.0
//   override def toString = s"(${round(x)}, ${round(y)}, ${round(z)})"


object KMeansRunner:

  val standardConfig = config(
    Key.exec.minWarmupRuns := 20,
    Key.exec.maxWarmupRuns := 40,
    Key.exec.benchRuns := 25,
    Key.verbose := false
  ) withWarmer(Warmer.Default())

  def main(args: Array[String]): Unit =
    val kMeans = KMeans()

    val numPoints = 500000
    val eta = 0.01
    val k = 32
    val points = kMeans.generatePoints(k, numPoints)
    val means = kMeans.initializeMeans(k, points)

    val seqtime =
      // Retrieve the support created to run the algorithm in parallel
      val parTasksupport = points.tasksupport
      // Create a support with only one executor to run the algorithm sequentially
      val seqPool = ForkJoinPool(1)
      val seqTasksupport = ForkJoinTaskSupport(seqPool)
      try
        // Run the the algorithm on the sequential support
        points.tasksupport = seqTasksupport
        means.tasksupport = seqTasksupport
        // Measure performances on the sequential runner
        standardConfig measure {
          kMeans.kMeans(points, means, eta)
        }
      finally
        // Restore the parallel support
        points.tasksupport = parTasksupport
        means.tasksupport = parTasksupport
        // Stop the sequential runner
        seqPool.shutdown()

    // Measure performances on the parallel runner
    val partime = standardConfig measure {
      kMeans.kMeans(points, means, eta)
    }

    println(s"sequential time: $seqtime")
    println(s"parallel time: $partime")
    println(s"speedup: ${seqtime.value / partime.value}")
