package calc

import com.raquo.laminar.api.L._
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits._

final case class Entrada(expressao: String, resultado: String)
final case class Tecla(rotulo: String, acao: () => Unit, classe: String)

object Main {
  private def fmt(s: String): String = s.replace("*", "×").replace("/", "÷").replace("-", "−")
  implicit val ec: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

  // ---------- Estado ----------
  private val expr       = Var("")
  private val anterior   = Var("")
  private val erro       = Var("")
  private val historico  = Var(List.empty[Entrada])
  private var recemCalculado = false

  // ---------- HTTP ----------
  private def http(metodo: String, url: String, corpo: Option[String] = None): Future[js.Dynamic] = {
    val init = js.Dynamic.literal(
      method = metodo,
      headers = js.Dictionary("Content-Type" -> "application/json")
    )
    corpo.foreach(c => init.body = c)
    dom.fetch(url, init.asInstanceOf[dom.RequestInit])
      .flatMap(_.json())
      .map(_.asInstanceOf[js.Dynamic])
  }

  private def carregarHistorico(): Unit =
    http("GET", "/api/historico").foreach { r =>
      val itens = r.itens.asInstanceOf[js.Array[js.Dynamic]].toList.map { i =>
        Entrada(i.expressao.asInstanceOf[String], i.resultado.asInstanceOf[String])
      }
      historico.set(itens)
    }

  // ---------- Ações ----------
  private def entrada(t: String): Unit = {
    erro.set("")
    if (recemCalculado && (t.head.isDigit || t == "." || t == "(")) expr.set(t)
    else expr.update(_ + t)
    recemCalculado = false
  }

  private def apagar(): Unit = { erro.set(""); expr.update(_.dropRight(1)) }
  private def limpar(): Unit = { erro.set(""); anterior.set(""); expr.set(""); recemCalculado = false }

  private def calcular(): Unit = {
    val e = expr.now()
    if (e.nonEmpty) {
      val corpo = js.JSON.stringify(js.Dynamic.literal(expressao = e.replace("×", "*").replace("÷", "/").replace("−", "-")))
      http("POST", "/api/calcular", Some(corpo)).foreach { r =>
        if (r.ok.asInstanceOf[Boolean]) {
          anterior.set(s"$e =")
          expr.set(r.resultado.asInstanceOf[String])
          recemCalculado = true
          erro.set(
            if (r.historicoSalvo.asInstanceOf[Boolean]) ""
            else "Aviso: MongoDB indisponível, histórico não salvo."
          )
          carregarHistorico()
        } else erro.set(r.erro.asInstanceOf[String])
      }
    }
  }

  private def limparHistorico(): Unit =
    http("DELETE", "/api/historico").foreach(_ => carregarHistorico())

  // ---------- Interface ----------
  private def num(t: String) = Tecla(t, () => entrada(t), "")
  private def op(rotulo: String, t: String) = Tecla(rotulo, () => entrada(t), "op")

  private val teclas = Seq(
    num("7"), num("8"), num("9"), op("÷", "÷"), op("(", "("), op(")", ")"), Tecla("C", () => limpar(), "acao"),
    num("4"), num("5"), num("6"), op("×", "×"), op("^", "^"), num("."), Tecla("⌫", () => apagar(), "acao"),
    num("1"), num("2"), num("3"), op("−", "−"), op("+", "+"), num("0"), Tecla("=", () => calcular(), "igual")
  )

  private def itemHistorico(i: Entrada) =
    li(
      onClick --> Observer[dom.MouseEvent](_ => { expr.set(i.resultado); recemCalculado = true }),
      span(cls := "he", fmt(i.expressao)),
      span(cls := "hr", s"= ${i.resultado}")
    )

  private def view: HtmlElement =
    div(
      cls := "calc",
      h1("Calculadora"),
      div(
        cls := "tela",
        div(cls := "anterior", child.text <-- anterior.signal),
        div(cls := "valor", child.text <-- expr.signal.map(e => if (e.isEmpty) "0" else e))
      ),
      div(cls := "erro", child.text <-- erro.signal),
      div(
        cls := "teclas",
        teclas.map(t => button(cls := s"tecla ${t.classe}", t.rotulo, onClick --> Observer[dom.MouseEvent](_ => t.acao())))
      ),
      div(
        cls := "historico",
        div(
          cls := "hist-topo",
          h2("Histórico"),
          button(cls := "limpar", "Limpar", onClick --> Observer[dom.MouseEvent](_ => limparHistorico()))
        ),
        ul(children <-- historico.signal.map(_.map(itemHistorico)))
      )
    )

  private def teclado(e: dom.KeyboardEvent): Unit =
    e.key match {
      case k if k.length == 1 && "0123456789.+-*/^()".contains(k) => entrada(k)
      case "Enter"     => e.preventDefault(); calcular()
      case "Backspace" => apagar()
      case "Escape"    => limpar()
      case _           => ()
    }

  def main(args: Array[String]): Unit = {
    dom.document.addEventListener("keydown", (e: dom.KeyboardEvent) => teclado(e))
    render(dom.document.getElementById("app"), view)
    carregarHistorico()
  }
}
