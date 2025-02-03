package com.icthh.xm.xmeplugin.yaml

import com.icthh.xm.xmeplugin.utils.YamlNode
import com.icthh.xm.xmeplugin.yaml.XmePluginSpecMetaInfoService.SpecState
import com.icthh.xm.xmeplugin.yaml.YamlPath.YamlPathArray
import com.icthh.xm.xmeplugin.yaml.YamlPath.YamlPathKey
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.yaml.snakeyaml.Yaml


object YamlUtils {
    fun readYaml(yamlContent: String): Any? {
        val settings = LoadSettings.builder().build()
        val load = Load(settings)
        return load.loadFromString(yamlContent)
    }

    inline fun <reified T> parseYaml(yamlFile: String): T {
        val result: T = Yaml().loadAs(yamlFile, T::class.java)
        return result
    }

    fun deepMerge(
        file1: String, map1: Map<*, *>,
        file2: String, map2: Map<*, *>,
        itemToFiles: MutableMap<Any, YamlPathArray>
    ): Map<*, *> {
        val merged = map1.toMutableMap()
        for ((key, value2) in map2) {
            val value1 = merged[key]
            merged[key] = when {
                // Both values are maps -> merge them recursively
                value1 is Map<*, *> && value2 is Map<*, *> ->
                    deepMerge(
                        file1, value1,
                        file2, value2,
                        itemToFiles
                    )
                // Both values are lists -> concatenate them
                value1 is List<*> && value2 is List<*> -> {
                    saveItem(file1, value1, itemToFiles)
                    saveItem(file2, value2, itemToFiles)
                    value1 + value2
                }
                // Otherwise, override with the new value
                else -> {
                    if (value2 is List<*>) {
                        saveItem(file2, value2, itemToFiles)
                    }
                    value2
                }
            }
        }
        return merged
    }

    fun saveItem(file: String, list: List<*>, itemToFiles: MutableMap<Any, YamlPathArray>) {
        list.filterNotNull().forEachIndexed { index, it ->
            itemToFiles.computeIfAbsent(it){ YamlPathArray(index, file) }
        }
    }

    fun buildYamlTree(obj: SpecState?): YamlNode {
        return buildYamlTree(obj?.state, null, obj?.itemToFiles ?: emptyMap())
    }

    fun buildYamlTree(obj: Any?, parent: YamlNode? = null, itemToFiles: Map<Any, YamlPathArray>): YamlNode {
        val node = YamlNode(parent, obj)
        when (obj) {
            is Map<*, *> -> {
                obj.forEach { (k, v) ->
                    val keyString = k.toString()
                    val childNode = buildYamlTree(v, node, itemToFiles)
                    node.addChild(YamlPathKey(keyString), childNode)
                }
            }
            is List<*> -> {
                obj.forEachIndexed { index, item ->
                    val childNode = buildYamlTree(item, node, itemToFiles)
                    node.addChild(itemToFiles[item] ?: YamlPathArray(index, "unknown"), childNode)
                }
            }
        }
        return node
    }

}

sealed class YamlPath {
    data class YamlPathKey(val key: String) : YamlPath()
    data class YamlPathArray(val index: Int, val filePath: String) : YamlPath()
}
