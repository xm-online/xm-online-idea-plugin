package com.icthh.xm.xmeplugin.yaml;

import com.icthh.xm.xmeplugin.utils.YamlNode;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.yaml.psi.YAMLValue;

public class YamlContext {
    public YAMLValue psiElement;
    public Object fullSpec;
    public YamlNode yamlNode;
    public Project project;

    private YamlContextHelper helper;

    public YamlContext() {
    }

    public YamlContext(YAMLValue psiElement, Object fullSpec, YamlNode yamlNode, Project project) {
        this.psiElement = psiElement;
        this.fullSpec = fullSpec;
        this.yamlNode = yamlNode;
        this.project = project;
        helper = new YamlContextHelper(psiElement, fullSpec, yamlNode, project);
    }

    public YAMLValue getPsiElement() {
        return psiElement;
    }
    public void setPsiElement(YAMLValue psiElement) {
        this.psiElement = psiElement;
    }

    public Object getFullSpec() {
        return fullSpec;
    }
    public void setFullSpec(Object fullSpec) {
        this.fullSpec = fullSpec;
    }

    public YamlNode getYamlNode() {
        return yamlNode;
    }
    public void setYamlNode(YamlNode yamlNode) {
        this.yamlNode = yamlNode;
    }

    public Project getProject() {
        return project;
    }
    public void setProject(Project project) {
        this.project = project;
    }

    // Methods available in js on context variable

    public void createTenantFile(String relativePathToConfigRepository, String body) {
        helper.createTenantFile(relativePathToConfigRepository, body);
    }

    public String getTenantName() {
        return helper.getTenantName();
    }

    public String getServiceName() {
        return helper.getServiceName();
    }

    public String translateToLepConvention(String key) {
        return helper.translateToLepConvention(key);
    }

    public void createFile(String relativePathToConfigRepository, String body) {
        helper.createFile(relativePathToConfigRepository, body);
    }

}
