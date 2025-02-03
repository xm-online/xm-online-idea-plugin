package com.icthh.xm.xmeplugin.utils;

import com.icthh.xm.xmeplugin.yaml.YamlPath;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class YamlNode {
    public YamlNode parent;
    public Object value;
    public Map<YamlPath, YamlNode> children = new HashMap<>();

    public YamlNode() {
    }

    public YamlNode(YamlNode parent, Object value) {
        this.parent = parent;
        this.value = value;
    }

    public void addChild(YamlPath path, YamlNode child) {
        children.put(path, child);
    }

    public YamlNode getChild(YamlPath path) {
        YamlNode yamlNode = children.get(path);
        if (yamlNode == null && path instanceof YamlPath.YamlPathArray array) {
            yamlNode = children.get(new YamlPath.YamlPathArray(array.getIndex(), "unknown"));
        }
        return yamlNode;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof YamlNode yamlNode)) return false;
        return Objects.equals(value, yamlNode.value) && Objects.equals(parent, yamlNode.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, parent);
    }

    @Override
    public String toString() {
        return "YamlNode{" +
            "value=" + value +
            ", children=" + parent +
            '}';
    }
}
