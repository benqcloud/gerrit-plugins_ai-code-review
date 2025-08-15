// Copyright (C) 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit;

import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.util.ManualRequestContext;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GerritClientAccount extends GerritClientBase {
  private final AccountCache accountCache;

  public GerritClientAccount(Configuration config, AccountCache accountCache) {
    super(config);
    this.accountCache = accountCache;
  }

  public boolean isDisabledUser(String authorUsername) {
    List<String> enabledUsers = config.getEnabledUsers();
    List<String> disabledUsers = config.getDisabledUsers();
    return !enabledUsers.contains(Configuration.ENABLED_USERS_ALL)
            && !enabledUsers.contains(authorUsername)
        || disabledUsers.contains(authorUsername)
        || isDisabledUserGroup(authorUsername);
  }

  public boolean isDisabledTopic(String topic) {
    List<String> enabledTopicFilter = config.getEnabledTopicFilter();
    List<String> disabledTopicFilter = config.getDisabledTopicFilter();
    return !enabledTopicFilter.contains(Configuration.ENABLED_TOPICS_ALL)
            && enabledTopicFilter.stream().noneMatch(topic::contains)
        || !topic.isEmpty() && disabledTopicFilter.stream().anyMatch(topic::contains);
  }

  protected Optional<Integer> getAccountId(String authorUsername) {
    try {
      return accountCache
          .getByUsername(authorUsername)
          .map(accountState -> accountState.account().id().get());
    } catch (Exception e) {
      log.error("Could not find account ID for username '{}'", authorUsername);
      return Optional.empty();
    }
  }

  public Integer getNotNullAccountId(String authorUsername) {
    return getAccountId(authorUsername)
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    String.format("Error retrieving '%s' account ID in Gerrit", authorUsername)));
  }

  private List<String> getAccountGroups(Integer accountId) {
    try (ManualRequestContext requestContext = config.openRequestContext()) {
      List<GroupInfo> groups = config.getGerritApi().accounts().id(accountId).getGroups();
      return groups.stream().map(g -> g.name).collect(toList());
    } catch (Exception e) {
      log.error("Could not find groups for account ID {}", accountId);
      return null;
    }
  }

  private boolean isDisabledUserGroup(String authorUsername) {
    List<String> enabledGroups = config.getEnabledGroups();
    List<String> disabledGroups = config.getDisabledGroups();

    log.debug("enabledGroups: {}", enabledGroups);
    log.debug("disabledGroups: {}", disabledGroups);

    if (enabledGroups.isEmpty() && disabledGroups.isEmpty()) {
      log.debug("No enabled/disabled groups configured.");
      return false;
    }

    Optional<Integer> accountId = getAccountId(authorUsername);
    log.debug("accountId for {}: {}", authorUsername, accountId);
    if (accountId.isEmpty()) {
      log.warn("No account ID found for user {}. Disabling user as precaution.", authorUsername);
      return true;
    }

    List<String> accountGroups = getAccountGroups(accountId.orElse(-1));
    log.debug("accountGroups: {}", accountGroups);
    if (accountGroups == null || accountGroups.isEmpty()) {
      if (!enabledGroups.isEmpty()) {
        log.warn(
            "enabledGroups is set and user {} is in no group. Disabling user.", authorUsername);
      } else {
        log.warn("User {} not in any groups. Disabling user as precaution.", authorUsername);
      }
      return true;
    }

    return !enabledGroups.contains(Configuration.ENABLED_GROUPS_ALL)
            && enabledGroups.stream().noneMatch(accountGroups::contains)
        || disabledGroups.stream().anyMatch(accountGroups::contains);
  }
}
