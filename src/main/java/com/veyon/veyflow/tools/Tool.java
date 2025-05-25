package com.veyon.veyflow.tools;

import java.util.List;

public class Tool {
    private String name;
    private String description;
    private List<Parameter> parametersSchema; // Changed from 'parameters' to avoid confusion with actual call params

    public Tool() {}

    public Tool(String name, String description, List<Parameter> parametersSchema) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Parameter> getParametersSchema() {
        return parametersSchema;
    }

    public void setParametersSchema(List<Parameter> parametersSchema) {
        this.parametersSchema = parametersSchema;
    }
}
