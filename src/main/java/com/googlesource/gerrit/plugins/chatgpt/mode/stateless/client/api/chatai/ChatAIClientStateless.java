package com.googlesource.gerrit.plugins.chatgpt.mode.stateless.client.api.chatai;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.interfaces.mode.common.client.api.chatgpt.IChatGptClient;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.chatgpt.ChatGptClient;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.chatgpt.ChatGptParameters;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.chatgpt.ChatGptTools;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.http.HttpClientWithRetry;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.chatgpt.*;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.chatgpt.mode.stateless.client.api.UriResourceLocatorStateless;
import com.googlesource.gerrit.plugins.chatgpt.mode.stateless.client.prompt.ChatGptPromptStateless;
import com.googlesource.gerrit.plugins.chatgpt.mode.stateless.model.api.chatgpt.ChatGptCompletionRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static com.googlesource.gerrit.plugins.chatgpt.config.Configuration.AUTH_HEADER_API_KEY;
import static com.googlesource.gerrit.plugins.chatgpt.utils.GsonUtils.getNoEscapedGson;

@Slf4j
@Singleton
public class ChatAIClientStateless extends ChatGptClient implements IChatGptClient {
    private static final int REVIEW_ATTEMPT_LIMIT = 3;

    private final HttpClientWithRetry httpClientWithRetry = new HttpClientWithRetry();

    @VisibleForTesting
    @Inject
    public ChatAIClientStateless(Configuration config) {
        super(config);
    }

    public ChatGptResponseContent ask(ChangeSetData changeSetData, GerritChange change, String patchSet)
            throws Exception {
        isCommentEvent = change.getIsCommentEvent();
        String changeId = change.getFullChangeId();
        log.info("Processing STATELESS ChatGPT Request with changeId: {}, Patch Set: {}", changeId, patchSet);
        for (int attemptInd = 0; attemptInd < REVIEW_ATTEMPT_LIMIT; attemptInd++) {
            HttpRequest request = createRequest(config, changeSetData, patchSet);
            log.debug("ChatGPT request: {}", request.toString());

            HttpResponse<String> response = httpClientWithRetry.execute(request);

            String body = response.body();
            log.debug("ChatGPT response body: {}", body);
            if (body == null) {
                throw new IOException("ChatGPT response body is null");
            }

            ChatGptResponseContent contentExtracted = extractContent(config, body);
            if (validateResponse(contentExtracted, changeId, attemptInd)) {
                return contentExtracted;
            }
        }
        throw new RuntimeException("Failed to receive valid ChatGPT response");
    }

    protected HttpRequest createRequest(Configuration config, ChangeSetData changeSetData, String patchSet) {
        URI uri = URI.create(config.getGptDomain() + UriResourceLocatorStateless.getChatResourceUri(config));
        log.debug("AIChat request URI: {}", uri);
        requestBody = createRequestBody(config, changeSetData, patchSet);
        log.debug("AIChat request body: {}", requestBody);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                .uri(uri)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        // depending on the aiType, add appropriate authorization header ( if required ).
        NameValuePair authHeader = getAuthorzationHeaderInfo();
        if (authHeader != null) {
            builder.header(authHeader.getName(), authHeader.getValue());
        }
        return builder.build();
    }

    private NameValuePair getAuthorzationHeaderInfo() {
        switch (config.getAIType()) {
            case AZUREOPENAI:
                return new BasicNameValuePair(AUTH_HEADER_API_KEY, config.getGptToken());
            case OLLAMA:
            case GENERIC:
                // by default no auth header is required for ollama so return null for no auth.
                // But if they wish to add some auth requirements, maybe for hosted setup,
                // then allow the header to be generically specified, same as the GENERIC configuration.
                return !Strings.isNullOrEmpty(config.getAuthHeaderName()) ?
                        new BasicNameValuePair(config.getAuthHeaderName(), config.getGptToken()) : null;
            case CHATGPT:
            default:
                // by default, or for chatGpt use bearer token, if someone is adding a new aiType, it can fall
                // into this block - or they will need to extend the cases above.
                return new BasicNameValuePair(HttpHeaders.AUTHORIZATION, "Bearer " + config.getGptToken());
        }
    }


    private String createRequestBody(Configuration config, ChangeSetData changeSetData, String patchSet) {
        ChatGptPromptStateless chatGptPromptStateless = new ChatGptPromptStateless(config, isCommentEvent);
        ChatGptRequestMessage systemMessage = ChatGptRequestMessage.builder()
                .role("system")
                .content(chatGptPromptStateless.getGptSystemPrompt())
                .build();
        ChatGptRequestMessage userMessage = ChatGptRequestMessage.builder()
                .role("user")
                .content(chatGptPromptStateless.getGptUserPrompt(changeSetData, patchSet))
                .build();

        ChatGptParameters chatGptParameters = new ChatGptParameters(config, isCommentEvent);
        ChatGptTool[] tools = new ChatGptTool[]{
                ChatGptTools.retrieveFormatRepliesTool()
        };
        ChatGptCompletionRequest chatGptCompletionRequest = ChatGptCompletionRequest.builder()
                .model(config.getGptModel())
                .messages(List.of(systemMessage, userMessage))
                .temperature(chatGptParameters.getGptTemperature())
                .stream(chatGptParameters.getStreamOutput())
                // Seed value is Utilized to prevent ChatGPT from mixing up separate API calls that occur in close
                // temporal proximity.
                .seed(chatGptParameters.getRandomSeed())
                .tools(tools)
                .toolChoice(ChatGptTools.retrieveFormatRepliesToolChoice())
                .build();

        return getNoEscapedGson().toJson(chatGptCompletionRequest);
    }
}
