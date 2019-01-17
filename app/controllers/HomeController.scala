package controllers

import javax.inject._
import anorm._, Macro.ColumnNaming
import play.api.db.Database
import play.api.mvc._
import play.api.libs.json.{Format, Json}

case class Product(title: String, price: Double, inventoryCount: Int)

object Product {
  implicit val format: Format[Product] = Json.format[Product]
  val parser = Macro.namedParser[Product](ColumnNaming.SnakeCase)
}

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject()(cc: ControllerComponents, db: Database) extends AbstractController(cc) {

  /**
    * Create an Action to render an HTML page.
    *
    * The configuration in the `routes` file means that this method
    * will be called when the application receives a `GET` request with
    * a path of `/`.
    */
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index("Welcome"))
  }

  def getProducts() = Action { implicit request: Request[AnyContent] =>
    var response = Ok(Json.parse("{}"))
    db.withConnection { implicit c =>
      val result: Either[List[Throwable], List[Product]] = SQL("SELECT * FROM products").fold(List[Product]()) { (list, row) =>
        val newProduct = Product(row[String]("title"), row[Double]("price"), row[Int]("inventory_count"))
        list :+ newProduct
      }
      result match {
        case Left(errors) => response = NotFound(errors.mkString(","))
        case Right(x) => response = Ok(Json.toJson(x))
      }
    }
    response
  }

  def buyProduct(id: String) = Action { implicit request: Request[AnyContent] =>
    var response = Ok(Json.parse("{}"))
    db.withConnection { implicit c =>
      val result =
        SQL("SELECT * FROM products WHERE title={id} LIMIT 1")
          .on("id" -> id)
          .as(Product.parser.single)
      result match {
        case x: Product if x.inventoryCount > 0 => {
          val newProduct = Product(x.title, x.price, x.inventoryCount - 1)
          SQL"UPDATE products SET inventory_count=${newProduct.inventoryCount} WHERE title=${newProduct.title}".executeUpdate()
          response = Ok(Json.toJson(newProduct))
        }
        case _ => response = Ok("Not enough to purchase")
      }
    }
    response
  }

  def reset() = Action { implicit request: Request[AnyContent] =>
    var response = Ok(Json.parse("{}"))
    db.withConnection { implicit c =>
      SQL"UPDATE products SET inventory_count=99".executeUpdate()
    }
    Redirect(routes.HomeController.getProducts())
  }


}
