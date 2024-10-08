package com.googlesource.gerrit.plugins.chatgpt.mode.common.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.localization.Localizer;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.openai.AIChatMessageItem;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.openai.AIChatRequestMessage;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.GerritClientData;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.googlesource.gerrit.plugins.chatgpt.settings.Settings.CHAT_GPT_ROLE_USER;

@Slf4j
public class ChatGptDataPromptRequests extends ChatGptDataPromptBase {
    protected AIChatMessageItem messageItem;
    protected List<AIChatRequestMessage> messageHistory;

    public ChatGptDataPromptRequests(
            Configuration config,
            ChangeSetData changeSetData,
            GerritClientData gerritClientData,
            Localizer localizer
    ) {
        super(config, changeSetData, gerritClientData, localizer);
        commentProperties = commentData.getCommentProperties();
    }

    public void addMessageItem(int i) {
        AIChatMessageItem messageItem = getMessageItem(i);
        messageItem.setId(i);
        messageItems.add(messageItem);
    }

    protected AIChatMessageItem getMessageItem(int i) {
        messageItem = super.getMessageItem(i);
        messageHistory = gptMessageHistory.retrieveHistory(commentProperties.get(i));
        AIChatRequestMessage request = extractLastUserMessageFromHistory();
        messageItem.setRequest(request.getContent());

        return messageItem;
    }

    private AIChatRequestMessage extractLastUserMessageFromHistory() {
        for (int i = messageHistory.size() - 1; i >= 0; i--) {
            if (CHAT_GPT_ROLE_USER.equals(messageHistory.get(i).getRole())) {
                return messageHistory.remove(i);
            }
        }
        throw new RuntimeException("Error extracting request from message history: no user message found in " +
                messageHistory);
    }
}
