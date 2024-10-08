package com.googlesource.gerrit.plugins.chatgpt.mode.common.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.interfaces.mode.common.client.prompt.IChatGptDataPrompt;
import com.googlesource.gerrit.plugins.chatgpt.localization.Localizer;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.openai.AIChatMessageItem;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.openai.AIChatRequestMessage;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.GerritClientData;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ChatGptDataPromptReview extends ChatGptDataPromptBase implements IChatGptDataPrompt {
    public ChatGptDataPromptReview(
            Configuration config,
            ChangeSetData changeSetData,
            GerritClientData gerritClientData,
            Localizer localizer
    ) {
        super(config, changeSetData, gerritClientData, localizer);
        commentProperties = new ArrayList<>(commentData.getCommentMap().values());
    }

    public void addMessageItem(int i) {
        AIChatMessageItem messageItem = getMessageItem(i);
        if (messageItem.getHistory() != null) {
            messageItems.add(messageItem);
        }
    }

    protected AIChatMessageItem getMessageItem(int i) {
        AIChatMessageItem messageItem = super.getMessageItem(i);
        List<AIChatRequestMessage> messageHistory = gptMessageHistory.retrieveHistory(commentProperties.get(i),
                true);
        setHistory(messageItem, messageHistory);

        return messageItem;
    }
}
