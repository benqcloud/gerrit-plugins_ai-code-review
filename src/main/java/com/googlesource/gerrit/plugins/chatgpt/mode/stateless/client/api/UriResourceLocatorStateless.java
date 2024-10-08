package com.googlesource.gerrit.plugins.chatgpt.mode.stateless.client.api;


import com.google.common.base.Strings;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;

public class UriResourceLocatorStateless {

    public static String getChatResourceUri(Configuration configuration) {
        // different resource Uri endpoints exist for each aiType we support.
        // some have backward compatible endpoints, but some do not.  This abstracts the knowledge
        // of which to call for which aiType set in the configuration settings.
        switch (configuration.getAIType()) {
            case CHATGPT:
                return chatCompletionsUri();
            case OLLAMA:
                // ollama has a compatible completions URI, but it didn't return the same data as
                // the main api/chat endpoint, so while this is being investigated we are using the new api.
                return ollamaChatUri();
            case AZUREOPENAI:
                // TODO: add the endpoint information here, along with the additional query param with the version
                // info.
                throw new UnsupportedOperationException("AzureOpenAi endpoint not yet supported.");
            case GENERIC:
                // generic ai development will require you to override the endpoint if it doesn't support the existing
                // chatCompletionsUri.. Usually you will provide the optional chatEndpoint configuration option.
                final String chatEndpoint = configuration.getChatEndpoint();
                // fallback onto chatCompletions api if nothing has been specified.
                return Strings.isNullOrEmpty(chatEndpoint) ? chatCompletionsUri() : chatEndpoint;
            default:
                throw new UnsupportedOperationException("Unsupported aiType, chat resource endpoint not yet described.");
        }
    }

    public static String chatCompletionsUri() {
        return "/v1/chat/completions";
    }

    public static String ollamaChatUri() {
        return "/api/chat";
    }
}
