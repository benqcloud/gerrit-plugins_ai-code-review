package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.prompt;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.stateful.client.prompt.IChatGptPromptStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class AIChatGptPromptStatefulRequests extends AIChatGptPromptStatefulBase implements IChatGptPromptStateful {
    public static String DEFAULT_GPT_ASSISTANT_INSTRUCTIONS_REQUESTS;

    public AIChatGptPromptStatefulRequests(Configuration config, ChangeSetData changeSetData, GerritChange change) {
        super(config, changeSetData, change);
        if (DEFAULT_GPT_ASSISTANT_INSTRUCTIONS_REQUESTS == null) {
            loadDefaultPrompts("promptsStatefulRequests");
        }
    }

    @Override
    public void addGptAssistantInstructions(List<String> instructions) {
       instructions.addAll(List.of(
                DEFAULT_GPT_ASSISTANT_INSTRUCTIONS_REQUESTS,
               getCommentRequestPrompt(changeSetData.getCommentPropertiesSize())
        ));
    }

    @Override
    public String getGptRequestDataPrompt() {
        if (changeSetData == null ) return null;
        return changeSetData.getGptDataPrompt();
    }
}
