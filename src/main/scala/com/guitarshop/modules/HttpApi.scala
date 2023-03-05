package com.guitarshop.modules

import com.guitarshop.http.auth.users._
import com.guitarshop.http.routes._
import com.guitarshop.http.routes.admin._

import cats.effect.Async
import cats.syntax.all._
import dev.profunktor.auth.JwtAuthMiddleware
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.middleware._

import scala.concurrent.duration.DurationInt

object HttpApi {
  def make[F[_]: Async](
      services: Services[F],
      programs: Programs[F],
      security: Security[F]
  ): HttpApi[F] =
    new HttpApi[F](services, programs, security) {}
}

sealed abstract class HttpApi[F[_]: Async] private (
    val services: Services[F],
    val programs: Programs[F],
    val security: Security[F]
) {

  private val adminMiddleware =
    JwtAuthMiddleware[F, AdminUser](security.adminJwtAuth.value, security.adminAuth.findUser)

  private val usersMiddleware =
    JwtAuthMiddleware[F, CommonUser](security.userJwtAuth.value, security.usersAuth.findUser)

  // Auth routes
  private val loginRoutes = LoginRoutes[F](security.auth).routes
  private val logoutRoutes = LogoutRoutes[F](security.auth).routes(usersMiddleware)
  private val usersRoutes = UserRoutes[F](security.auth).routes

  // Open routes
  private val healthRoutes = HealthRoutes[F](services.healthCheck).routes
  private val brandRoutes = BrandRoutes[F](services.brands).routes
  private val categoryRoutes = CategoryRoutes[F](services.categories).routes
  private val itemRoutes = ItemRoutes[F](services.items).routes

  // Secured routes
  private val cartRoutes = CartRoutes[F](services.cart).routes(usersMiddleware)
  private val checkoutRoutes = CheckoutRoutes[F](programs.checkout).routes(usersMiddleware)
  private val orderRoutes = OrderRoutes[F](services.orders).routes(usersMiddleware)

  // Admin routes
  private val adminBrandRoutes = AdminBrandRoutes[F](services.brands).routes(adminMiddleware)
  private val adminCategoryRoutes =
    AdminCategoryRoutes[F](services.categories).routes(adminMiddleware)
  private val adminItemRoutes = AdminItemRoutes[F](services.items).routes(adminMiddleware)

  // Combining all the http routes
  private val openRoutes: HttpRoutes[F] =
    healthRoutes <+> itemRoutes <+> brandRoutes <+> categoryRoutes <+> loginRoutes <+> usersRoutes <+> logoutRoutes <+> cartRoutes <+> orderRoutes <+> checkoutRoutes

  private val adminRoutes: HttpRoutes[F] =
    adminBrandRoutes <+> adminCategoryRoutes <+> adminItemRoutes

  private val routes: HttpRoutes[F] = Router(
    version.v1 -> openRoutes,
    version.v1 + "/admin" -> adminRoutes
  )

  private val middleware: HttpRoutes[F] => HttpRoutes[F] = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    } andThen { http: HttpRoutes[F] =>
      CORS(http)
    } andThen { http: HttpRoutes[F] =>
      Timeout(60.seconds)(http)
    }
  }

  private val loggers: HttpApp[F] => HttpApp[F] = {
    { http: HttpApp[F] =>
      RequestLogger.httpApp(logHeaders = true, logBody = true)(http)
    } andThen { http: HttpApp[F] =>
      ResponseLogger.httpApp(logHeaders = true, logBody = true)(http)
    }
  }

  val httpApp: HttpApp[F] = loggers(middleware(routes).orNotFound)
}
