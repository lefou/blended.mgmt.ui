package blended.mgmt.app.components

import blended.mgmt.app.backend.{JsonWebToken, UserInfo}
import blended.mgmt.app.state.{AppEvent, LoggedIn, MgmtAppState}
import blended.mgmt.app.theme.Theme
import blended.security.BlendedPermissions
import blended.ui.common.Logger
import blended.ui.material.MaterialUI._
import com.github.ahnfelt.react4s._
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.window

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

case class MgmtLoginComponent(state: P[MgmtAppState]) extends Component[AppEvent] {

  private[this] val log = Logger[MgmtLoginComponent]

  private[this] case class LoginDetails(
    host: String = "",
    port: Integer = 0,
    user : String = "",
    pwd : String = "",
    errorMsg : Option[String] = None
  ) {
    def isValid : Boolean = !(host.isEmpty || user.isEmpty || pwd.isEmpty || port <= 0)
  }

  private[this] val initialized : State[Boolean] = State(false)
  private[this] val loginDetails : State[LoginDetails] = State(LoginDetails())

  private[this] def performLogin(get: Get) : Unit = {

    implicit val eCtxt : ExecutionContext = get(state).actorSystem.dispatcher
    val details = get(loginDetails)

    val requestUrl = s"http://${details.host}:${details.port}/login/"

    Ajax.post(
      url = requestUrl,
      headers = Map("Authorization" -> ("Basic " + window.btoa(details.user + ":" + details.pwd)))
    ).onComplete {
      case Failure(e) =>
        log.error(e)(e.getMessage)
        loginDetails.modify(_.copy(errorMsg = Some("Failed to login")))
      case Success(s) =>
        if (s.status == 200) {
          val token = s.responseText

          val decoded = JsonWebToken.decode(token)
          val json = decoded.getOrElse("permissions", "").asInstanceOf[String]

          log.info(json)

          BlendedPermissions.fromJson(json) match {
            case Failure(e) =>
              loginDetails.modify(_.copy(errorMsg = Some(s"Could not decode permissions [${e.getMessage}]")))
            case Success(p) =>
              log.info(s"Successfully logged in [${details.user}]")
              emit(LoggedIn(
                details.host, details.port,
                UserInfo(
                  details.user, token, p
                )
              ))
          }
        } else {
          loginDetails.modify(_.copy(errorMsg = Some(s"Login failed with status code [${s.status}]")))
        }
    }
  }

  // scalastyle:off magic.number
  private[this] def hostAndPort(details: LoginDetails) : Node = E.div(
    S.flexDirection("row"),
    S.flex("1 100%"),
    TextField(
      A.onChangeText{ t => loginDetails.modify (_.copy(host = t) ) },
      S.width.percent(70),
      S.paddingRight.px(Theme.spacingUnit),
      J("id", "host"),
      J("label", "host"),
      J("value", details.host)
    ),
    TextField(
      S.width.percent(30),
      A.onChangeText{ t =>
        try {
          val newPort : Integer = Integer.parseInt(t)
          loginDetails.modify (_.copy(port = newPort) )
        } catch {
          case NonFatal(_) =>
            loginDetails.modify(_ => details)
            log.warn(s"could not parse [$t] into an Integer")
        }
      },
      J("id", "port"),
      J("label", "Port"),
      J("value", details.port),
      J("type", "number")
    )
  )
  // scalastyle:on magic.number

  private[this] def userAndPwd(details : LoginDetails) : JsTag = Tags(
    TextField(
      Theme.LoginComponent,
      A.onChangeText{ t => loginDetails.modify(_.copy(user = t)) },
      J("id", "user"),
      J("label", "Username"),
      J("value", details.user),
      J("fullWidth", true)
    ),
    TextField(
      Theme.LoginComponent,
      A.onChangeText{ t => loginDetails.modify(_.copy(pwd = t)) },
      J("id", "password"),
      J("label", "Password"),
      J("value", details.pwd),
      J("type", "password"),
      J("fullWidth", true)
    )
  )

  private[this] val title : JsTag = Tags(
    Toolbar(
      Theme.LoginTitle,
      Typography(
        J("color", "inherit"),
        J("variant", "headline"),
        J("component", "h3"),
        Text("Login to continue")
      )
    ),
    E.div(S.height.px(Theme.spacingUnit))
  )

  private[this] def loginButton(get : Get) : JsTag = {

    val details = get(loginDetails)

    Button(
      Theme.LoginComponent,
      J("id", "submit"),
      J("variant", "contained"),
      J("color", "primary"),
      J("fullWidth", true),
      J("disabled", !details.isValid),
      Text("Login"),
      A.onClick { _ => performLogin(get) }
    )
  }

  private[this] def showLoginForm(get : Get) : Node = {

    val s = get(state)

    if (!get(initialized)) {
      loginDetails.set(LoginDetails(s.host, s.port))
      initialized.set(true)
    }

    val details = get(loginDetails)

    val children : Seq[JsTag] = Seq(
      Theme.LoginPaper,
      J("elevation", 1),
      title,
      hostAndPort(details),
      userAndPwd(details),
    ) ++ details.errorMsg.map { msg =>
      Typography(
        J("color", "error"),
        Text(msg)
      )
    }.toSeq ++ Seq(
      loginButton(get)
    )

    Paper(children:_*)
  }

  override def render(get: Get): Node = {
    showLoginForm(get)
  }
}
