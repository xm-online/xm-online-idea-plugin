# xme-plugin

## Build

./gradlew clean shadowJar

## Installation


  Download the [latest release](https://github.com/xm-online/xm-online-idea-plugin/releases) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

<!-- Plugin description -->
- XME.digital plugin for IntelliJ IDEA
- Update configuration in memory.
- lepContext, commons, tenantConfig autocomplete
- yaml specifications inspections and quickfixes
<!-- Plugin description end -->

## Plugin features:
### Configuration and deploy in memory


#### For microservice project

You can setup automatic linking lep to microservice using plugin.
(Plugin will automatically create symlink to lep-s)

#### _In this setup will work lepContext, lepContext.inArgs, commons, tenantConfig autocomplete!_

Environment configuration:
![image](https://github.com/user-attachments/assets/a4c05b91-d657-4240-9300-ce0c1d0c0081)

- 1 - Add configuration
- 2 - Select path to configuration project folder
- 3 - select tenants
- 4 - optionally specify env url and credential (need for in memory update)
- 5 - select mode of deploy (deploy uncommited change or deploy difference between two branches)
- 6 - after you select environment lep folder will be linked automatically (7)

#### For configuration project

![image](https://github.com/user-attachments/assets/96d28e6c-45ed-4699-9230-df4a75977a93)

- 1 - environments configuration
- 2 - add new environment and put all information and credentials
- 3 - select mode of deploy
- 4 - deploy uncommited change
- 5 - deploy difference between two branches
- 6 - select environment
- 7 - deploy to selected environment all "changes files"

"Update current file in memory" also available in context method

----------------

![image](https://github.com/user-attachments/assets/518961f2-736b-49ac-8507-608383883259)

- 1 - this icon mean that somebody change this file with you
- 2 - you can click to file path and see difference (3) between server file state and you local state

### Project tree icons, extends and domain

Plugin read logoUrl or favicon and use it as icon of tenant folder in project tree.

### File search by lep name conventions

Plugin during file search automatically convert typeKey/key to file name by lep convention,
and search both variants.

![image](https://github.com/user-attachments/assets/88cfa53a-0e9a-41f0-a0b9-f97d9736cceb)

### LepContext autocomplete

If lep folder linked inside microservice, plugin will provide autocomplete for `lepContext`

### inArgs autocomplete

If lep folder linked inside microservice, plugin will try to detect method that override current lep by name of file 
and provide autocomplete for `lepContext.inArgs` using method signature.

### Tenant config autocomplete / type inference / navigations

Plugin read and autocomplete tenant-config.yml inside lep.

![tenantConfigImprovment](https://github.com/user-attachments/assets/d6781b59-dcb5-4148-8a0d-e4e232ca7aa1)

### Commons autocomplete and refactoring

Plugin can autocomplete lepContext.commons in lep-s.

To declare signature of commons you need to declare method with same name that have commons file

![image](https://github.com/user-attachments/assets/d9a7a6a4-5517-4e12-9201-a7a484633912)

If method signature declared, plugin will provide rename refactoring for lep commons-s.

### XmEntity spec icon and color provider

Plugin read XmEntity spec and provide icon and colors.

## Custom yaml specification support without programing

You can add own custom inspections, references, actions, autocompletes using configuration.

#### Video instruction:

[![XME plugin customizations](https://img.youtube.com/vi/0KaHaCRpKEs/0.jpg)](https://youtu.be/0KaHaCRpKEs)

To make customization:
1 - create folder `xme-plugin` in root of config project folder.
2 - create file with any name but with .yml extension (for example: `any-name-of-file.yml`) in `xme-plugin` folder
3 - put to this file your customizations

#### Psi path template
To identify yml node in file plugin using small custom dsl that identify path to element

Examples:
- simple path to element: `specifications[].inspections[].elementPath`
- path with field assert: `specifications[key='${context.yamlNode.value}'].inspections[severity='ERROR'].elementPath`
- path with target node value assert: `specifications[].inspections[].elementPath('value of elementPath')`



#### File structure:

```yaml
# Top-level list defining specifications
specifications:
  - key: # (String) A unique key identifying this specification (using for caching)
    jsonSchemaUrl: # (String) Path or URL to a JSON schema for validation. Can be relative path.
    fileAntPatterns:
      - # (String) Each ant pattern used to match files related to this specification
    inspections:
      - key: # (String) A unique key identifying this inspection (using for caching)
        elementPath: # (String) YAML path to element inside file. Example: `specifications[].inspections[].elementPath`
        condition: # (String) JavaScript expression that have to return true or false 
        errorMessageTemplate: # (String) es6 template string for the message shown when condition is true
        severity: # (String) Severity level (INFO, WARN, or ERROR)
        includeFunctions: 
          - # (String) Name(s) of JavaScript function(s) to be included or used by this inspection or templates
        action: # (String) Quickfix JavaScript expression to fix the issue
        actionMessageTemplate: # (String) Quickfix name es6 template string
    references:
      - key: # (String) A unique key identifying this reference entry (using for caching)
        elementPath: # (String) YAML path to element inside file. Example: `specifications[].references[].elementPath`
        severity: # (String) Severity level (INFO, WARN, or ERROR)
        errorMessageTemplate: # (String) Message shown when the referenced item is missing. es6 template string
        includeFunctions:
          - # (String) Name(s) of JavaScript function(s) to be included in templates and other js expressions
        reference:
          type: # (String) Reference type ("file" or "element")
          filePathTemplates:
            - # (String) es6 template string defining file paths to look for
          elementPathTemplates:
            - # (String) es6 template string defining paths to reference inside yaml file
              # simple path to element: specifications[].inspections[].elementPath 
              # path with field assert: specifications[key='${context.yamlNode.value}'].inspections[severity='ERROR'].elementPath
              # path with target node value assert: specifications[].inspections[].elementPath('value of elementPath')
          required: # (Boolean) Indicates if the message with defined severity will be shown when the reference is missing
    injections:
      - elementPath: # (String) YAML path where language injection is applied
        language: # (String) Language to be injected (e.g., json, javascript, sql, file-reference, jvm-field-name)
        jsonSchemaUrl: # [optional] (String) Path or URL to a JSON schema for this injected language
    autocompletes:
      - elementPath: # (String) YAML path where autocomplete is enabled
        variants: 
          - # [optional] (String) Static list of autocomplete suggestions
        variantsPath: #  [optional](String) es6 string template of path to dynamically retrieve autocomplete suggestions
        variantsExpression: # (String) JavaScript expression returning dynamic suggestions (have to contains return keyword)
        includeFunctions:
          - # (String) Name(s) of JavaScript function(s) to be included or used by this autocomplete
    actions: # actions that will be displayed in context menu inside yaml files of current specification
      - key: # (String) A unique key identifying this action
        nameTemplate: # (String) es6 template string for name of action
        elementPath: # [optional] (String) YAML path where the action is applicable
        condition: # [optional] (String) JavaScript expression determining if the action should be available
        action: # (String) JavaScript expression executed when the action is triggered
        includeFunctions:
          - # (String) Name(s) of JavaScript function(s) to be included or used by this action
        successMessageTemplate: # (String) Message template shown upon a successful action
        successActions: # In success message we can add follow-up actions like "open generated file"
          - action: # (String) JavaScript expression executed when the action is triggered
            actionNameTemplate: # (String) Template used for naming the follow-up action

# Top-level list defining JavaScript functions. Functions available only when included
jsFunctions:
  - key: # (String) A unique key identifying the JavaScript function
    body: # (String) The full JavaScript code for this function
```

In example js expression we can use context variable.
Source code:
https://github.com/xm-online/xm-online-idea-plugin/blob/master/src/main/java/com/icthh/xm/xmeplugin/yaml/YamlContext.java

Feel free to add needed methods or missing api to this class.

For now this class contains next fields:
- `YAMLValue psiElement` low level intellij representation of current yaml node
- `Object fullSpec` full specification object deserialized from yaml files of specification
- `Project project` intellij idea project representation
- `YamlNode yamlNode` representation of current yaml node

YamlNode contains:
- `value` - yaml node value (string, number, map, list etc)
- `parent` - parent yaml node.

If current node is field value, parent node will be object that contains this field.
If current node is item of list value, parent node will be object that contains field with this list.

Parent node it is always object.
