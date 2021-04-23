/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package autoend

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.command.executeCommand
import net.mamoe.mirai.console.extension.PluginComponentStorage
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.testing.BaseTestingPlugin

object Main : BaseTestingPlugin("AutoEnd") {

    override fun PluginComponentStorage.onLoad() {
        contributePostStartupExtension {
            launch {
                logger.info("Console will auto stop after 5s")
                delay(5000)

                logger.info("Stopping....")
                ConsoleCommandSender.executeCommand("/stop")
            }
        }
    }
}
