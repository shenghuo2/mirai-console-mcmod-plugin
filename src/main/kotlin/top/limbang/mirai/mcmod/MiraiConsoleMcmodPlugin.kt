package top.limbang.mirai.mcmod

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.NudgeEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.event.nextEventOrNull
import net.mamoe.mirai.event.subscribeGroupMessages
import net.mamoe.mirai.message.data.content
import top.limbang.mirai.mcmod.service.Filter
import top.limbang.mirai.mcmod.service.MessageHandle
import top.limbang.mirai.mcmod.service.MinecraftModService
import top.limbang.mirai.mcmod.service.SearchResult


object MiraiConsoleMcmodPlugin : KotlinPlugin(
    JvmPluginDescription(
        id = "top.limbang.mirai-console-mcmod-plugin",
        version = "1.1.1",
    ) {
        author("limbang")
        info("""mc百科查询""")
    }
) {
    private val service = MinecraftModService()

    override fun onEnable() {
        McmodPluginData.reload()
        McmodPluginCompositeCommand.register()
        // 读取查询自定义命令
        val module = McmodPluginData.queryCommand[Filter.MODULE] ?: "百科模组"
        val data = McmodPluginData.queryCommand[Filter.DATA] ?: "百科资料"
        val courseOfStudy = McmodPluginData.queryCommand[Filter.COURSE_OF_STUDY] ?: "百科教程"

        globalEventChannel().subscribeGroupMessages {
            (startsWith(module) or startsWith(data) or startsWith(courseOfStudy)) reply {
                val msg = it.trim()
                val searchMessage = when {
                    msg.startsWith(module) -> SearchMessage(Filter.MODULE, msg.substringAfter(module))
                    msg.startsWith(data) -> SearchMessage(Filter.DATA, msg.substringAfter(data))
                    msg.startsWith(courseOfStudy) -> SearchMessage(Filter.COURSE_OF_STUDY,
                        msg.substringAfter(courseOfStudy))
                    else -> return@reply "未知错误!!!"
                }
                if (searchMessage.key.isEmpty()) return@reply "查询内容不能为空!!!"

                service.clear()
                var nextEvent: GroupMessageEvent
                var list: List<SearchResult>
                do {
                    list = service.getSearchList(searchMessage.key, searchMessage.filter, 7)
                    if (list.isEmpty()) return@reply "未搜索到结果，请更换关键字重试。"
                    else if (list.size == 1) return@reply getSearchResults(0,list,this)
                    
                    group.sendMessage(service.searchListToString(list, group, bot))
                    // 获取下一条消息事件
                    nextEvent = nextEventOrNull(30000) { next -> next.sender == sender } ?: return@reply "等待回复超时,请重新查询。"
                    if(!service.getNextPage() && service.getSearchResultsListSize() <= 0) break
                } while (nextEvent.message.content == "P")

                try {
                    return@reply getSearchResults(nextEvent.message.content.toInt(),list,this)
                } catch (e: NumberFormatException) {
                    return@reply "请正确回复序号,此次查询已取消,请重新查询。"
                }
            }

        }
        // 监听戳一戳消息并回复帮助
        globalEventChannel().subscribeAlways<NudgeEvent> {
            if (target.id == bot.id) {
                subject.sendMessage(
                    "Minecraft百科查询插件使用说明:\n" +
                            "查询物品:$data 加物品名称\n" +
                            "查询模组:$module 加模组名称\n" +
                            "查询教程:$courseOfStudy 加教程名称\n" +
                            "资料均来自:mcmod.cn"
                )
            }
        }
    }
}

suspend fun getSearchResults(serialNumber: Int, list: List<SearchResult>, event: GroupMessageEvent): Any{
    if (serialNumber >= list.size) return "输入序号大于可查询数据,此次查询已取消,请重新查询。"

    val searchResults = list[serialNumber]
    return when (searchResults.filter) {
        Filter.MODULE -> MessageHandle.moduleHandle(searchResults.url, event)
        Filter.DATA -> MessageHandle.dataHandle(searchResults.url, event)
        Filter.COURSE_OF_STUDY -> MessageHandle.courseOfStudyHandle(searchResults.url, event)
        else -> Unit
    }
}

data class SearchMessage(val filter: Filter, val key: String)

/**
 * ### 插件数据
 */
object McmodPluginData : AutoSavePluginData("mcmod") {
    val queryCommand: MutableMap<Filter, String> by value()
}

/**
 * ### 插件指令
 */
object McmodPluginCompositeCommand : CompositeCommand(
    MiraiConsoleMcmodPlugin, "mcmod"
) {
    @SubCommand("setQueryCommand", "查询命令")
    suspend fun CommandSender.setQueryCommand(type: Filter, command: String) {
        sendMessage("原查询$type 命令<${McmodPluginData.queryCommand[type]}>更改为<$command>,重启后生效")
        McmodPluginData.queryCommand[type] = command
    }
}
