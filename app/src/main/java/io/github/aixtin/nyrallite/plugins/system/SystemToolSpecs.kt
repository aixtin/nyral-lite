package io.github.aixtin.nyrallite.plugins.system

import io.github.aixtin.nyrallite.contract.ToolSpec

/** 系统工具的模型侧声明：名称、描述与参数 JSON Schema。 */
internal object SystemToolSpecs {

    private const val PARAMS_SEND_NOTIFICATION = """{
        "type":"object",
        "properties":{
            "title":{"type":"string","description":"通知标题，64字以内"},
            "body":{"type":"string","description":"通知正文，400字以内"},
            "channelId":{"type":"string","description":"可选通知渠道ID，默认 nyral_generic"}
        },
        "required":["title","body"]
    }"""

    private const val PARAMS_DIAL_PHONE = """{
        "type":"object",
        "properties":{
            "phone":{"type":"string","description":"要拨打的电话号码，支持 + 开头的国际格式"}
        },
        "required":["phone"]
    }"""

    private const val PARAMS_OPEN_BROWSER = """{
        "type":"object",
        "properties":{
            "url":{"type":"string","description":"要访问的完整 URL"}
        },
        "required":["url"]
    }"""

    private const val PARAMS_SHARE_TEXT = """{
        "type":"object",
        "properties":{
            "text":{"type":"string","description":"要分享的文本内容"},
            "title":{"type":"string","description":"分享选择器的标题"}
        },
        "required":["text"]
    }"""

    private const val PARAMS_START_FOREGROUND = """{
        "type":"object",
        "properties":{
            "taskId":{"type":"string","description":"任务唯一ID，用于后续 stop_foreground_task"},
            "interval_minutes":{"type":"string","description":"每隔多少分钟发一次通知，最小1，默认15"},
            "notification_title":{"type":"string","description":"每次通知的标题"},
            "notification_body":{"type":"string","description":"每次通知的正文"},
            "title":{"type":"string","description":"前台常驻通知的标题（任务正在运行时显示）"},
            "body":{"type":"string","description":"前台常驻通知的正文"},
            "run_once":{"type":"string","description":"true 表示只提醒一次然后退出，false 表示一直循环，默认 false"}
        },
        "required":["taskId","interval_minutes"]
    }"""

    private const val PARAMS_STOP_FOREGROUND = """{
        "type":"object",
        "properties":{
            "taskId":{"type":"string","description":"要停止的任务ID"}
        },
        "required":["taskId"]
    }"""

    val ALL: List<ToolSpec> = listOf(
        ToolSpec(
            name = "send_notification",
            description = "向用户的手机发送一条系统通知。用户会在系统通知栏看到，App 不需要在前台也能收到。适合在长任务完成、重要提醒、或者你想主动联系用户时使用。",
            parametersJson = PARAMS_SEND_NOTIFICATION,
        ),
        ToolSpec(
            name = "dial_phone",
            description = "打开系统拨号器准备拨打一个电话号码。只是打开拨号界面，用户确认后才真正拨出，不会自动打电话。",
            parametersJson = PARAMS_DIAL_PHONE,
        ),
        ToolSpec(
            name = "open_browser",
            description = "打开系统浏览器访问指定 URL。会调起用户选择的默认浏览器 App。",
            parametersJson = PARAMS_OPEN_BROWSER,
        ),
        ToolSpec(
            name = "share_text",
            description = "把一段文字分享到其他 App（微信、QQ、邮件等）。会弹出系统分享选择器让用户挑目标。",
            parametersJson = PARAMS_SHARE_TEXT,
        ),
        ToolSpec(
            name = "start_foreground_task",
            description = "启动一个后台长任务（会拉起系统前台通知常驻）。目前支持周期性提醒：每隔 N 分钟给用户发一条通知。任务启动后会一直跑直到你调 stop_foreground_task。适合闹钟、定时提醒、周期性检查等场景。注意：间隔不要低于 1 分钟，否则 Android 系统会限制。",
            parametersJson = PARAMS_START_FOREGROUND,
        ),
        ToolSpec(
            name = "stop_foreground_task",
            description = "停止一个正在运行的前台长任务，taskId 要和启动时保持一致。停止后前台通知消失。",
            parametersJson = PARAMS_STOP_FOREGROUND,
        ),
    )
}
