package calc

import org.mongodb.scala._
import org.mongodb.scala.model.Sorts.descending

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/** Avaliador de expressões: + - * / ^ ( ) e números decimais. */
object Avaliador {
  def avaliar(entrada: String): Double = {
    val v = new Parser(entrada.filterNot(_.isWhitespace)).analisar()
    if (v.isNaN || v.isInfinity) throw new ArithmeticException("Resultado inválido")
    v
  }

  def formatar(v: Double): String =
    if (v == v.toLong.toDouble && math.abs(v) < 1e15) v.toLong.toString
    else BigDecimal(v).round(new java.math.MathContext(12)).bigDecimal.stripTrailingZeros.toPlainString

  private class Parser(s: String) {
    private var i = 0
    private def tem(c: Char) = i < s.length && s(i) == c

    def analisar(): Double = {
      if (s.isEmpty) throw new IllegalArgumentException("Expressão vazia")
      val v = soma()
      if (i < s.length) throw new IllegalArgumentException(s"Caractere inesperado: '${s(i)}'")
      v
    }

    private def soma(): Double = {
      var v = termo()
      while (tem('+') || tem('-')) {
        val op = s(i); i += 1
        val r = termo()
        v = if (op == '+') v + r else v - r
      }
      v
    }

    private def termo(): Double = {
      var v = potencia()
      while (tem('*') || tem('/')) {
        val op = s(i); i += 1
        val r = potencia()
        if (op == '*') v *= r
        else {
          if (r == 0) throw new ArithmeticException("Divisão por zero")
          v /= r
        }
      }
      v
    }

    private def potencia(): Double = {
      val base = unario()
      if (tem('^')) { i += 1; math.pow(base, potencia()) } else base
    }

    private def unario(): Double =
      if (tem('-')) { i += 1; -unario() }
      else if (tem('+')) { i += 1; unario() }
      else atomo()

    private def atomo(): Double =
      if (tem('(')) {
        i += 1
        val v = soma()
        if (!tem(')')) throw new IllegalArgumentException("Parêntese não fechado")
        i += 1
        v
      } else {
        val ini = i
        while (i < s.length && (s(i).isDigit || s(i) == '.')) i += 1
        if (ini == i) throw new IllegalArgumentException("Expressão incompleta")
        Try(s.substring(ini, i).toDouble).getOrElse(throw new IllegalArgumentException("Número inválido"))
      }
  }
}

/** Acesso ao MongoDB (coleção "historico"). */
object Repositorio {
  private val uri = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017/?serverSelectionTimeoutMS=3000")
  private val espera = 6.seconds

  private lazy val colecao: MongoCollection[Document] =
    MongoClient(uri)
      .getDatabase(sys.env.getOrElse("MONGO_DB", "calculadora"))
      .getCollection("historico")

  def salvar(expressao: String, resultado: String): Try[Unit] = Try {
    val doc = Document("expressao" -> expressao, "resultado" -> resultado, "criadoEm" -> System.currentTimeMillis())
    Await.result(colecao.insertOne(doc).toFuture(), espera)
    ()
  }

  def listar(): Try[Seq[(String, String)]] = Try {
    Await.result(colecao.find().sort(descending("criadoEm")).limit(20).toFuture(), espera).map { d =>
      val e = d.get("expressao").map(_.asString().getValue).getOrElse("")
      val r = d.get("resultado").map(_.asString().getValue).getOrElse("")
      (e, r)
    }
  }

  def limpar(): Try[Unit] = Try {
    Await.result(colecao.deleteMany(Document()).toFuture(), espera)
    ()
  }
}

object Main extends cask.MainRoutes {
  override def host: String = "0.0.0.0"
  override def port: Int = sys.env.getOrElse("PORT", "8080").toInt

  @cask.get("/")
  def index() = cask.Redirect("/static/index.html")

  @cask.postJson("/api/calcular")
  def calcular(expressao: String) =
    Try(Avaliador.avaliar(expressao)) match {
      case Success(v) =>
        val texto = Avaliador.formatar(v)
        val salvo = Repositorio.salvar(expressao, texto).isSuccess
        ujson.Obj("ok" -> true, "resultado" -> texto, "historicoSalvo" -> salvo)
      case Failure(e) =>
        ujson.Obj("ok" -> false, "erro" -> e.getMessage)
    }

  @cask.get("/api/historico")
  def historico() =
    Repositorio.listar() match {
      case Success(itens) =>
        ujson.Obj(
          "ok" -> true,
          "itens" -> ujson.Arr.from(itens.map { case (e, r) => ujson.Obj("expressao" -> e, "resultado" -> r) })
        )
      case Failure(_) => ujson.Obj("ok" -> false, "itens" -> ujson.Arr())
    }

  @cask.delete("/api/historico")
  def limparHistorico() = {
    Repositorio.limpar()
    ujson.Obj("ok" -> true)
  }

  @cask.staticResources("/static")
  def estaticos() = "public"

  initialize()
new Thread(() => {
  while (Try(new java.net.Socket("localhost", port).close()).isFailure) Thread.sleep(100)
  val dados = sys.props("java.io.tmpdir") + "\\calc-edge"
  val edge = Seq("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe", "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe").find(p => new java.io.File(p).exists).getOrElse("msedge")
  val janela = new ProcessBuilder(edge, s"--app=http://localhost:$port", s"--user-data-dir=$dados", "--window-size=1500,500")
  .start()
  janela.waitFor()
  sys.exit(0)
}).start()
}