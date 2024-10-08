package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.prompt.IChatAIDataPrompt;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatMessageItem;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

@Slf4j
public class AIChatDataPrompt {
    private final IChatAIDataPrompt chatGptDataPromptHandler;

    public AIChatDataPrompt(
            Configuration config,
            ChangeSetData changeSetData,
            GerritChange change,
            GerritClientData gerritClientData,
            Localizer localizer
    ) {
        chatGptDataPromptHandler = AIChatPromptFactory.getChatGptDataPrompt(
                config,
                changeSetData,
                change,
                gerritClientData,
                localizer
        );
    }

    public String buildPrompt() {
        for (int i = 0; i < chatGptDataPromptHandler.getCommentProperties().size(); i++) {
            chatGptDataPromptHandler.addMessageItem(i);
        }
        List<AIChatMessageItem> messageItems = chatGptDataPromptHandler.getMessageItems();
        return messageItems.isEmpty() ? "" : getGson().toJson(messageItems);
    }
}
