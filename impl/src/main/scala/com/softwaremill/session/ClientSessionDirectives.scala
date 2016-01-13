package com.softwaremill.session

import akka.http.scaladsl.server.{Directive1, Directive0}
import akka.http.scaladsl.server.Directives._

trait ClientSessionDirectives {
  /**
   * Set the session cookie with the session content. The content is signed, optionally encrypted and with
   * an optional expiry date.
   */
  def setSession[T](magnet: ClientSessionManagerMagnet[T, T]): Directive0 =
    setCookie(magnet.manager.createCookie(magnet.input))

  /**
   * Read a session from the session cookie, wrapped in [[SessionResult]] describing the possible
   * success/failure outcomes.
   */
  def session[T](magnet: ClientSessionManagerMagnet[T, Unit]): Directive1[SessionResult[T]] =
    optionalCookie(magnet.manager.config.clientSessionCookieConfig.name)
      .map {
        case Some(cookie) => magnet.manager.decode(cookie.value)
        case None => SessionResult.NoSession
      }

  /**
   * Read an optional session from the session cookie.
   */
  def optionalSession[T](magnet: ClientSessionManagerMagnet[T, Unit]): Directive1[Option[T]] =
    session(magnet).map(_.toOption)

  /**
   * Read a required session from the session cookie.
   */
  def requiredSession[T](magnet: ClientSessionManagerMagnet[T, Unit]): Directive1[T] =
    optionalSession(magnet).flatMap {
      case None => reject(magnet.manager.cookieMissingRejection)
      case Some(data) => provide(data)
    }

  /**
   * Invalidate the session cookie
   */
  def invalidateSession[T](magnet: ClientSessionManagerMagnet[T, Unit]): Directive0 =
    deleteCookie(magnet.manager.createCookieWithValue("").copy(maxAge = None))

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.clientSessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchOptionalSession[T](magnet: ClientSessionManagerMagnet[T, Unit]): Directive1[Option[T]] = {
    optionalSession(magnet).flatMap { d =>
      d.fold(pass){ value =>
        implicit val ma: ClientSessionManager[T] = magnet.manager
        setSession(ClientSessionManagerMagnet.forClientManager(value))
      } & provide(d)
    }
  }

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.clientSessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchRequiredSession[T](magnet: ClientSessionManagerMagnet[T, Unit]): Directive1[T] = {
    import magnet.manager
    requiredSession(magnet).flatMap { d => setSession(ClientSessionManagerMagnet.forClientManager(d)) & provide(d) }
  }
}

object ClientSessionDirectives extends ClientSessionDirectives

trait ClientSessionManagerMagnet[T, In] {
  implicit def manager: ClientSessionManager[T]
  def input: In
}

object ClientSessionManagerMagnet {
  implicit def forClientManager[T, In](_input: In)(implicit _manager: ClientSessionManager[T]): ClientSessionManagerMagnet[T, In] =
    new ClientSessionManagerMagnet[T, In] {
      override val manager = _manager
      override val input = _input
    }

  implicit def forSessionManager[T, In](_input: In)(implicit _manager: SessionManager[T]): ClientSessionManagerMagnet[T, In] =
    new ClientSessionManagerMagnet[T, In] {
      override val manager = _manager.clientSession
      override val input = _input
    }
}