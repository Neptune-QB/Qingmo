package com.qingmo.app.xiaomo

import com.qingmo.app.data.model.DramaHighlight

/**
 * 模块注册中心 — 管理所有互动模块的注册/注销/调度
 */
object ModuleRegistry {

    private val modules = mutableListOf<InteractionModule>()

    /** 注册模块 */
    fun register(module: InteractionModule) {
        modules.removeAll { it.moduleId == module.moduleId }
        modules.add(module)
        modules.sortBy { it.priority }
        module.onRegister()
    }

    /** 注销模块 */
    fun unregister(moduleId: String) {
        modules.find { it.moduleId == moduleId }?.let { m ->
            m.onUnregister()
        }
        modules.removeAll { it.moduleId == moduleId }
    }

    /** 获取当前所有已注册的模块（按优先级排序） */
    fun getActiveModules(): List<InteractionModule> = modules.toList()

    /**
     * 根据高光点的 widget_type 查找匹配的处理器模块
     * @return 匹配的模块，如果没找到返回 null
     */
    fun findHandler(highlight: DramaHighlight): InteractionModule? {
        return modules.firstOrNull { it.canHandle(highlight) }
    }

    /** 清空所有已注册模块 */
    fun clear() {
        modules.forEach { it.onUnregister() }
        modules.clear()
    }
}
