import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.*
import io.javalin.Javalin
import java.io.IOException
import java.net.URL

/**
 * https://www.baeldung.com/jackson-deserialization
 */
class ImagesDeserializer : StdDeserializer<List<String>>(List::class.java) {
    @Throws(IOException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<String> {
        val node: JsonNode = p.codec.readTree(p)
        val imagesString = node.asText()
        return imagesString.split(",").map { it.trim() }
    }
}

data class Product(
    val id: String,
    val title: String,
    val description: String,
    val price: Int,
    @JsonDeserialize(using = ImagesDeserializer::class)
    val images: List<String>,
    val category_id: Int,
    val category: Category?,
    val slug: String
)

data class Category(
    val id: Int,
    val slug: String,
    val name: String,
    val image: String
)

inline fun <reified T> parseObject(json: String): List<T> {
    val mapper = ObjectMapper().registerKotlinModule()
    return mapper.readValue(json)
}

fun joinCatsOnProd(products: List<Product>, categories: List<Category>): List<Product> {
    val catMap = categories.associateBy { it.id }
    return products.map { product: Product ->
        val category = catMap[product.category_id] ?: error("Category not found for: ${product.id}-${product.title}")
        product.copy(category = category)
    }
}

fun main() {

    val catURL = "https://raw.githubusercontent.com/PlatziLabs/fake-api-backend/master/src/dataset/categories.json"
    val prodUrl = "https://raw.githubusercontent.com/PlatziLabs/fake-api-backend/master/src/dataset/products.json"

    val catJson = URL(catURL).readText()
    val categories = parseObject<Category>(catJson)

    val prodJson = URL(prodUrl).readText()
    val products = parseObject<Product>(prodJson)

    val joined = joinCatsOnProd(products, categories)

    /**
     * https://javalin.io/plugins/cors#getting-started
     */
    val allow = "vite-react-webstore.vercel.app"
    val javalin = Javalin.create { config ->
        config.bundledPlugins.enableCors { cors ->
            cors.addRule {
                it.allowHost("http://localhost:5173")
                it.allowHost(allow)
            }
        }
    }.start(8000)

    javalin.get("/api/products") { ctx ->
        ctx.json(joined)
    }

    javalin.get("/api/products/1") { ctx ->
        ctx.json(joined.first())
    }

    println("Server running on 8000/api/products")
}
