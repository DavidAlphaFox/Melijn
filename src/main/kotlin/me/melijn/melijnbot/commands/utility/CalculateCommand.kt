package me.melijn.melijnbot.commands.utility

import me.melijn.melijnbot.internals.command.AbstractCommand
import me.melijn.melijnbot.internals.command.CommandCategory
import me.melijn.melijnbot.internals.command.ICommandContext
import me.melijn.melijnbot.internals.threading.TaskManager
import me.melijn.melijnbot.internals.utils.message.sendRsp
import me.melijn.melijnbot.internals.utils.message.sendSyntax
import org.mariuszgromada.math.mxparser.Expression
import java.lang.management.ManagementFactory

class CalculateCommand : AbstractCommand("command.calculate") {

    init {
        id = 220
        name = "calculate"
        aliases = arrayOf("calc", "calculator", "math")
        commandCategory = CommandCategory.UTILITY
        cooldown = 2000
    }

    override suspend fun execute(context: ICommandContext) {
        if (context.rawArg.isBlank()) {
            sendSyntax(context)
            return
        }

        var pid = 9999999L
        val t = object : Thread("calc ${context.contextTime}") {
            override fun run() {
                pid = ManagementFactory.getRuntimeMXBean().pid
                var exp = try {
                    Expression(context.rawArg).calculate().toString()
                } catch (t: InterruptedException) {
                    "Took too long"
                } catch (t: Throwable) {
                    "error"
                }
                exp = if (exp.endsWith(".0")) exp.dropLast(2) else exp
                TaskManager.async { sendRsp(context, "Result: $exp") }
            }
        }
        context.initCooldown()
        t.start()
//        delay(2_000)
//        val m: Method = Thread::class.java.getDeclaredMethod("interrupt0")
//        m.isAccessible = true
//        m.invoke(t)

        // https://man7.org/linux/man-pages/man2/tkill.2.html us syscall
//        Runtime.getRuntime().exec("kill $pid")
    }
}