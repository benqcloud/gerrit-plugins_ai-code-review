package com.googlesource.gerrit.plugins.chatgpt.interfaces.mode.common.client.api.chatgpt;

import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.openai.AIChatResponseContent;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.ChangeSetData;

public interface IChatGptClient {
    AIChatResponseContent ask(ChangeSetData changeSetData, GerritChange change, String patchSet)
            throws Exception;
    String getRequestBody();
}
