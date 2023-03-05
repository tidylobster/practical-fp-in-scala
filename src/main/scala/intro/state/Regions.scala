package intro.state

import cats.effect._
import cats.effect.std.{Semaphore, Supervisor}
import cats.effect.unsafe.implicits.global

import scala.concurrent.duration._

trait Sleep {
  def randomSleep: IO[Unit] =
    IO(scala.util.Random.nextInt(100)).flatMap { ms =>
      IO.sleep((ms + 700).millis)
    }.void
}

object Regions extends IOApp.Simple with Sleep {
  def p1(sem: Semaphore[IO]): IO[Unit] =
    sem.permit.surround(IO.println("Running P1") >> randomSleep)

  def p2(sem: Semaphore[IO]): IO[Unit] =
    sem.permit.surround(IO.println("Running P2") >> randomSleep)

  override def run: IO[Unit] =
    Supervisor[IO].use { s=>
      Semaphore[IO](1).flatMap { sem =>
        s.supervise(p1(sem).foreverM).void *>
          s.supervise(p2(sem).foreverM).void *>
          IO.sleep(5.seconds).void
      }
    }
}

object LeakyState extends IOApp.Simple with Sleep {
  lazy val sem: Semaphore[IO] = Semaphore[IO](1).unsafeRunSync()

  def launchMissiles: IO[Unit] =
    sem.permit.surround(IO.println("Something bad happened"))

  def p1: IO[Unit] =
    sem.permit.surround(IO.println("Running P1")) >> randomSleep

  def p2: IO[Unit] =
    sem.permit.surround(IO.println("Running P2")) >> randomSleep

  def run: IO[Unit] =
    Supervisor[IO].use { s =>
      s.supervise(launchMissiles) *>
        s.supervise(p1.foreverM) *>
        s.supervise(p2.foreverM) *>
        IO.sleep(5.seconds).void
    }
}
