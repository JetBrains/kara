package kotlinx.html.bootstrap

import kara.link
import kotlinx.html.*
import java.util.*

inline fun HtmlBodyTag.pills(body: UL.()->Unit) = ul("nav nav-pills", contents = body)
inline fun HtmlBodyTag.content(body: DIV.()->Unit) = div("tab-content", contents = body)
fun HtmlBodyTag.pane(name: String, active: Boolean = false, body: DIV.()->Unit) = div {
    addClass("tab-pane")
    if (active)
        addClass("active")
    this.id = name
    body()
}

fun HtmlBodyTag.tabs(body: UL.()->Unit) = ul("nav nav-tabs", contents = body)

class PagesBuilder {
    class Item(val id: String, val title: String, val content: HtmlBodyTag.() -> Unit)

    val items = ArrayList<Item>()

    fun item(name: String, title: String, content : HtmlBodyTag.() -> Unit) {
        items.add(Item(name, title, content))
    }
}

fun HtmlBodyTag.tabs(activeName: String, body: PagesBuilder.() -> Unit) {
    val builder = PagesBuilder()
    builder.body()

    tabs {
        for (item in builder.items) {
            item(activeName == item.id) {
                a {
                    attribute("data-toggle", "tab")
                    href = "#${item.id}".link()
                    +item.title
                }
            }
        }
    }

    content {
        for (item in builder.items) {
            pane(item.id, activeName == item.id) {
                item.content(this)
            }
        }
    }
}
