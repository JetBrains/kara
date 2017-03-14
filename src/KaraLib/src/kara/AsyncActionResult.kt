package kara

import kara.internal.logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * @author max
 */
class AsyncResult(val asyncContext: AsyncContext, val appContext: ApplicationContext, val params: RouteParameters, val allowHttpSession: Boolean, val body : ActionContext.() -> ActionResult) : ActionResult {
    internal val createdAt = System.currentTimeMillis()
    internal val deadline = createdAt + asyncContext.timeout

    var timed_out = false

    fun exired() = deadline < System.currentTimeMillis()

    init {
        asyncContext.addListener(object: AsyncListener {
            override fun onComplete(e: AsyncEvent) {
            }

            override fun onTimeout(e: AsyncEvent) {
                timed_out = true
            }

            override fun onStartAsync(e: AsyncEvent) {
            }

            override fun onError(e: AsyncEvent) {
            }
        })
    }

    override fun writeResponse(context: ActionContext) {
    }
}

private val asyncExecutors: ExecutorService by lazy {
    Executors.newFixedThreadPool(ActionContext.tryGet()?.config?.tryGet("kara.asyncThreads")?.toInt() ?: 4, {
        Executors.defaultThreadFactory().newThread(it).apply {
            name = "Kara async request executor"
        }
    })
}

@Suppress("unused")
class AsyncServletContextListener: ServletContextListener {

    override fun contextInitialized(p0: ServletContextEvent?) {
        //nothing
    }

    override fun contextDestroyed(p0: ServletContextEvent?) {
        asyncExecutors.shutdown()
    }
}

val karaAsyncExecutorsQueueSize: Int get() = (asyncExecutors as ThreadPoolExecutor).queue.size

private fun AsyncResult.execute() {
    try {
        if (timed_out) return

        checkExpired()

        val context = ActionContext(appContext, asyncContext.request as HttpServletRequest, asyncContext.response as HttpServletResponse, params, allowHttpSession)
        context.withContext {
            val result = context.body()

            context.flushSessionCache()
            if (!timed_out) {
                result.writeResponse(context)
            }
        }
    }
    finally {
        if (!timed_out) {
            checkExpired()
            asyncContext.complete()
        }
        else {
            logger.info("Asynchronous task timed out. See Connector's 'asyncTimeout' attribute in server.xml")
        }
    }
}

private fun AsyncResult.checkExpired() {
    if (exired()) {
        logger.warn("Asynchronous task timed out (${System.currentTimeMillis() - deadline}ms ago), but onTimeout event was not triggered.")
    }
}

fun ActionContext.async(body: ActionContext.() -> ActionResult): ActionResult {
    val asyncContext = request.startAsync(request, response)
    val asyncResult = AsyncResult(asyncContext, appContext, params, allowHttpSession, body)

    asyncExecutors.submit {
        asyncResult.execute()
    }

    return asyncResult
}
