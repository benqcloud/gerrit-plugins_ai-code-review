package com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.openai;

import lombok.Data;

@Data
public class AIChatToolChoice {
    private String type;
    private Function function;

    @Data
    public static class Function {
        private String name;
    }
}
