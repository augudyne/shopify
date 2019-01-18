package controllers

import javax.inject._
import anorm._
import Macro.ColumnNaming
import play.api.db.Database
import play.api.mvc._
import play.api.libs.json.{Format, Json}

import scala.util.Try

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

  private val HEADER_KEY_CART = "X-Cart-Items"
  private val ERROR_NO_CART = "No Cart in session"

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
        case Right(x) => response = Ok(Json.prettyPrint(Json.toJson(x.sortBy(_.title))))
      }
    }
    response
  }

  def start() = Action { implicit request: Request[AnyContent] =>
    Redirect(routes.HomeController.cart()).withSession((HEADER_KEY_CART, ""))
  }

  def cart() = Action { implicit request: Request[AnyContent] =>
    request.session.get(HEADER_KEY_CART).map { cart: String =>
      val contents = cart.split("\t ").filterNot(p => p.isEmpty)
      contents.groupBy { item => item }
        .map { group => (group._1, group._2.length) }
        .toList
        .sortBy{ e => e._1 }
    } match {
      case Some(x) => Ok(Json.prettyPrint(Json.toJson(x)))
      case None => Ok(ERROR_NO_CART)
    }
  }

  def addProduct(id: String) = Action { implicit request: Request[AnyContent] =>
    var response = Ok(ERROR_NO_CART)
    db.withConnection { implicit c =>
      Try {
        SQL"SELECT * FROM products WHERE title=$id LIMIT 1".as(Product.parser.single)
      } match {
        case util.Success(value) if request.session.get(HEADER_KEY_CART).isEmpty => response = Ok(ERROR_NO_CART)
        case util.Success(value) => {
          val newCart = request.session.get(HEADER_KEY_CART).get ++ s"\t $id"
          response = Redirect(routes.HomeController.cart()).withSession((HEADER_KEY_CART, newCart))
        }
        case scala.util.Failure(exception) => response =  Ok(s"No item `$id`")
      }
    }
    response
  }

  def removeProduct(id: String) = Action { implicit request: Request[AnyContent] =>
    request.session.get(HEADER_KEY_CART).map { cart =>
      if (cart.contains(id)) {
        cart.replaceFirst(id, "")
      } else {
        ""
      }
    } match {
      case Some(cart) if !cart.isEmpty =>  Redirect(routes.HomeController.cart()).withSession((HEADER_KEY_CART, cart))
      case Some(cart) =>  Ok(s"You do not have $id in your cart")
      case _ => Ok(ERROR_NO_CART)
    }
  }

  def purchase() = Action { implicit request: Request[AnyContent] =>
    request.session.get(HEADER_KEY_CART).map { cart: String =>
      val contents = cart.split("\t ").filterNot(p => p.isEmpty)
      contents.groupBy { item => item }
        .map { group => (group._1, group._2.length) }
    } match {
      case Some(shoppingList) => {
        db.withConnection { implicit c =>
          println(shoppingList.keySet.toList)
          val inventory = SQL("SELECT * FROM products WHERE title in ({items})")
            .on("items" -> shoppingList.keySet.toList)
            .as(Product.parser.*)
          println(inventory)

          inventory.find { inventoryProduct =>
            inventoryProduct.inventoryCount < shoppingList.getOrElse(inventoryProduct.title, 0)
          } match {
            case Some(item) => Ok(s"Not enough ${item.title}. Requested ${shoppingList.getOrElse(item.title, 0)} but only have ${item.inventoryCount}")
            case None => {
              println(s"Buying all of ${inventory.mkString(",")}")
              inventory.foreach { product =>
                SQL"UPDATE products SET inventory_count=${product.inventoryCount - shoppingList.getOrElse(product.title, 0)} WHERE title=${product.title}".executeUpdate()
              }
              Redirect(routes.HomeController.getProducts()).withNewSession
            }
          }
        }
      }
      case None => Ok(ERROR_NO_CART)
    }
  }

  /**
    * Deprecated
    * @param id The item to purchase
    * @return The new inventory stock of the item
    */
  @Deprecated("January 17th 2019. Start a cart session")
  def buyProduct(id: String) = Action { implicit request: Request[AnyContent] =>
    var response = Ok(Json.parse("{}"))
    db.withConnection { implicit c =>
      Try {
        SQL("SELECT * FROM products WHERE title={id} LIMIT 1")
          .on("id" -> id)
          .as(Product.parser.single)
        match {
          case x: Product if x.inventoryCount > 0 => {
            val newProduct = Product(x.title, x.price, x.inventoryCount - 1)
            SQL"UPDATE products SET inventory_count=${newProduct.inventoryCount} WHERE title=${newProduct.title}".executeUpdate()
            response = Ok(Json.toJson(newProduct))
          }
          case _ => response = Ok("Not enough to purchase")
        }
      } match {
        case scala.util.Failure(exception) => NotFound(s"$id is not a product in the catalogue")
      }
    }
    response
  }

  def reset() = Action { implicit request: Request[AnyContent] =>
    var response = Ok(Json.parse("{}"))
    db.withConnection { implicit c =>
      SQL"UPDATE products SET inventory_count=5".executeUpdate()
    }
    Redirect(routes.HomeController.getProducts())
  }
}
