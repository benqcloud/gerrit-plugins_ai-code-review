package com.googlesource.gerrit.plugins.chatgpt.interfaces.mode.common.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.openai.AIChatMessageItem;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.gerrit.GerritComment;

import java.util.List;

public interface IChatGptDataPrompt {
    void addMessageItem(int i);
    List<GerritComment> getCommentProperties();
    List<AIChatMessageItem> getMessageItems();
}
