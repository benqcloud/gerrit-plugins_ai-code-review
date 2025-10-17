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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.code;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritCodeRange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatReplyItem;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.patch.code.CodeFinderDiff;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.patch.diff.DiffContent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CodeFinder {
  private static final String PUNCTUATION_REGEX = "([()\\[\\]{}<>:;,?&+\\-*/%|=])";
  private static final String BEGINNING_DIFF_REGEX = "(?:^|\n)[+\\-]";
  private static final String ENDING_ELLIPSIS_REGEX = "\\.\\.\\.\\W*$";

  private final String NON_PRINTING_REPLACEMENT;
  private final String PUNCTUATION_REPLACEMENT;
  private final String PLACEHOLDER_REGEX;
  private final List<CodeFinderDiff> codeFinderDiffs;

  private int commentedLine;
  private Pattern commentedCodePattern;
  private GerritCodeRange currentCodeRange;
  private GerritCodeRange closestCodeRange;

  public CodeFinder(List<CodeFinderDiff> codeFinderDiffs, String randomPlaceholder) {
    this.codeFinderDiffs = codeFinderDiffs;
    NON_PRINTING_REPLACEMENT = "\\\\E" + randomPlaceholder + "\\\\Q";
    PUNCTUATION_REPLACEMENT = "\\\\E" + randomPlaceholder + "\\\\$1" + randomPlaceholder + "\\\\Q";
    PLACEHOLDER_REGEX = "(?:" + randomPlaceholder + ")+";
  }

  public GerritCodeRange findCommentedCode(AIChatReplyItem replyItem, int commentedLine) {
    this.commentedLine = commentedLine;
    updateCodePattern(replyItem);
    currentCodeRange = null;
    closestCodeRange = null;
    for (CodeFinderDiff codeFinderDiff : codeFinderDiffs) {
      for (Field diffField : DiffContent.class.getDeclaredFields()) {
        String diffCode = getDiffItem(diffField, codeFinderDiff.getContent());
        if (diffCode != null) {
          TreeMap<Integer, Integer> charToLineMapItem = codeFinderDiff.getCharToLineMap();
          try {
            findCodeLines(diffCode, charToLineMapItem);
          } catch (IllegalArgumentException e) {
            log.warn(
                "Could not retrieve line number from charToLineMap.\nDiff Code = {}", diffCode, e);
          }
        }
      }
    }

    if (closestCodeRange == null) {
      log.error("\nfindCommentedCode: CTCSOC proprietary approach, across diffs\n");
      findCodeLinesAcrossDiffs();
    }
    if (closestCodeRange == null) {
      log.error("\nfindCommentedCode: CTCSOC proprietary approach, commentedLine\n");
      return createCodeRangeFromCommentedLine(commentedLine);
    }

    log.error("\nfindCommentedCode: Google community approach, diffs each by each\n");
    return closestCodeRange;
  }

  /** Attempts to find code that spans across multiple CodeFinderDiff pieces */
  private void findCodeLinesAcrossDiffs() {
    if (codeFinderDiffs.size() < 2) {
      return; // Need at least 2 diffs for cross-diff matching
    }

    // Build combined content from consecutive diffs
    for (int i = 0; i < codeFinderDiffs.size() - 1; i++) {
      for (int j = i + 1; j < codeFinderDiffs.size(); j++) {
        String combinedDiffCode = buildCombinedDiffCode(i, j);
        TreeMap<Integer, Integer> combinedCharToLineMap = buildCombinedCharToLineMap(i, j);

        if (combinedDiffCode != null && combinedCharToLineMap != null) {
          try {
            findCodeLines(combinedDiffCode, combinedCharToLineMap);
            // If we found a match in this combined range, we can stop
            if (closestCodeRange != null) {
              return;
            }
          } catch (IllegalArgumentException e) {
            log.warn("Could not retrieve line number from combined charToLineMap", e);
          }
        }
      }
    }
  }

  /** Builds combined diff code from a range of CodeFinderDiffs */
  private String buildCombinedDiffCode(int startIdx, int endIdx) {
    StringBuilder combinedCode = new StringBuilder();

    for (int i = startIdx; i <= endIdx; i++) {
      CodeFinderDiff diff = codeFinderDiffs.get(i);
      String diffCode = getFirstDiffCode(diff);
      if (diffCode != null) {
        combinedCode.append(diffCode);
        // Add a newline between diffs to maintain proper line counting
        if (i < endIdx) {
          combinedCode.append("\n");
        }
      }
    }

    return combinedCode.length() > 0 ? combinedCode.toString() : null;
  }

  /** Builds combined charToLineMap from a range of CodeFinderDiffs */
  private TreeMap<Integer, Integer> buildCombinedCharToLineMap(int startIdx, int endIdx) {
    TreeMap<Integer, Integer> combinedMap = new TreeMap<>();
    int currentCharOffset = 0;

    for (int i = startIdx; i <= endIdx; i++) {
      CodeFinderDiff diff = codeFinderDiffs.get(i);
      TreeMap<Integer, Integer> diffMap = diff.getCharToLineMap();

      if (diffMap != null) {
        for (java.util.Map.Entry<Integer, Integer> entry : diffMap.entrySet()) {
          // Adjust character positions by the current offset
          combinedMap.put(entry.getKey() + currentCharOffset, entry.getValue());
        }

        // Update offset for next diff (include the newline we added)
        String diffCode = getFirstDiffCode(diff);
        if (diffCode != null) {
          currentCharOffset += diffCode.length() + 1; // +1 for the newline
        }
      }
    }

    return combinedMap.isEmpty() ? null : combinedMap;
  }

  /** Gets the first available diff code from a CodeFinderDiff */
  private String getFirstDiffCode(CodeFinderDiff codeFinderDiff) {
    for (Field diffField : DiffContent.class.getDeclaredFields()) {
      String diffCode = getDiffItem(diffField, codeFinderDiff.getContent());
      if (diffCode != null) {
        return diffCode;
      }
    }
    return null;
  }

  private Integer findClosestValidLine(int targetLine) {
    Integer closestLine = null;
    int minDistance = Integer.MAX_VALUE;

    for (CodeFinderDiff codeFinderDiff : codeFinderDiffs) {
      TreeMap<Integer, Integer> charToLineMap = codeFinderDiff.getCharToLineMap();
      if (charToLineMap != null && !charToLineMap.isEmpty()) {
        for (Integer lineNumber : charToLineMap.values()) {
          int distance = Math.abs(lineNumber - targetLine);
          if (distance < minDistance) {
            minDistance = distance;
            closestLine = lineNumber;
          }
        }
      }
    }

    return closestLine;
  }

  private GerritCodeRange createCodeRangeFromCommentedLine(int commentedLine) {
    Integer closestValidLine = findClosestValidLine(commentedLine);

    if (closestValidLine != null) {
      // Find the exact character positions for this line in the code snippets
      CharacterRange charRange = findCharacterRangeForLine(closestValidLine);

      if (charRange != null) {
        return GerritCodeRange.builder()
            .startLine(closestValidLine)
            .endLine(closestValidLine)
            .startCharacter(charRange.startChar)
            .endCharacter(charRange.endChar)
            .build();
      } else {
        // Fallback if we can't find exact character positions
        return GerritCodeRange.builder()
            .startLine(closestValidLine)
            .endLine(closestValidLine)
            .startCharacter(0)
            .endCharacter(1) // At least 1 character wide for visibility
            .build();
      }
    }

    log.warn("No valid lines found in diff, using commented line {} as fallback", commentedLine);
    return GerritCodeRange.builder()
        .startLine(commentedLine)
        .endLine(commentedLine)
        .startCharacter(0)
        .endCharacter(1)
        .build();
  }

  /** Finds the exact character range for a given line number across all code snippets */
  private CharacterRange findCharacterRangeForLine(int targetLine) {
    for (CodeFinderDiff codeFinderDiff : codeFinderDiffs) {
      TreeMap<Integer, Integer> charToLineMap = codeFinderDiff.getCharToLineMap();
      String diffCode = getFirstDiffCode(codeFinderDiff);

      if (charToLineMap != null && diffCode != null) {
        // Find the character positions that map to this line
        Integer lineStartChar = null;
        Integer lineEndChar = null;

        for (java.util.Map.Entry<Integer, Integer> entry : charToLineMap.entrySet()) {
          if (entry.getValue() == targetLine) {
            if (lineStartChar == null) {
              lineStartChar = entry.getKey();
            }
            lineEndChar = entry.getKey();
          } else if (lineStartChar != null) {
            // We've moved to the next line, so break
            break;
          }
        }

        if (lineStartChar != null) {
          // Calculate the actual end of the line
          int actualLineEnd = findLineEndPosition(diffCode, lineStartChar);
          return new CharacterRange(lineStartChar, actualLineEnd);
        }
      }
    }

    return null;
  }

  /** Finds the end position of a line starting from a given character position */
  private int findLineEndPosition(String diffCode, int startPosition) {
    if (startPosition >= diffCode.length()) {
      return startPosition;
    }

    // Find the next newline character or end of string
    int newlinePos = diffCode.indexOf('\n', startPosition);
    if (newlinePos == -1) {
      return diffCode.length() - 1;
    }

    return newlinePos;
  }

  /** Helper class to store character range */
  private static class CharacterRange {
    final int startChar;
    final int endChar;

    CharacterRange(int startChar, int endChar) {
      this.startChar = startChar;
      this.endChar = endChar;
    }
  }

  private void updateCodePattern(AIChatReplyItem replyItem) {
    String commentedCode =
        replyItem
            .getCodeSnippet()
            .replaceAll(BEGINNING_DIFF_REGEX, "")
            .replaceAll(ENDING_ELLIPSIS_REGEX, "")
            .trim();
    String commentedCodeRegex = Pattern.quote(commentedCode);
    // Generalize the regex to capture snippets where existing sequences of non-printing chars have
    // been modified
    // from the original code
    commentedCodeRegex = commentedCodeRegex.replaceAll("\\s+", NON_PRINTING_REPLACEMENT);
    // Generalize the regex to capture snippets where non-printing chars have been removed from
    // around the
    // punctuation marks of the original code
    commentedCodeRegex = commentedCodeRegex.replaceAll(PUNCTUATION_REGEX, PUNCTUATION_REPLACEMENT);
    // Remove redundant empty literal escape sequences that could have resulted from previous
    // substitutions
    commentedCodeRegex = commentedCodeRegex.replaceAll("\\\\Q\\\\E", "");
    // Obtain a functional regex to match code snippets without relying on non-printing chars
    commentedCodeRegex = commentedCodeRegex.replaceAll(PLACEHOLDER_REGEX, "\\\\s*");
    // Remove any detected trailing matching sequence of non-printing chars
    commentedCodeRegex = commentedCodeRegex.replaceAll("\\\\s\\*$", "");
    commentedCodePattern = Pattern.compile(commentedCodeRegex);
  }

  private double calcCodeDistance(GerritCodeRange range, int fromLine) {
    return Math.abs((range.endLine - range.startLine) / 2 - fromLine);
  }

  private String getDiffItem(Field diffField, DiffContent diffItem) {
    try {
      return (String) diffField.get(diffItem);
    } catch (IllegalAccessException e) {
      log.error("Error while processing file difference (diff type: {})", diffField.getName(), e);
      return null;
    }
  }

  private int getLineNumber(TreeMap<Integer, Integer> charToLineMapItem, int position) {
    Integer floorPosition = charToLineMapItem.floorKey(position);
    if (floorPosition == null) {
      throw new IllegalArgumentException("Position: " + position);
    }
    return charToLineMapItem.get(floorPosition);
  }

  private int getLineCharacter(String diffCode, int position) {
    // Return the offset relative to the nearest preceding newline character if found, `position`
    // otherwise
    return position - diffCode.substring(0, position).lastIndexOf("\n") - 1;
  }

  private void findCodeLines(String diffCode, TreeMap<Integer, Integer> charToLineMapItem)
      throws IllegalArgumentException {
    Matcher codeMatcher = commentedCodePattern.matcher(diffCode);
    while (codeMatcher.find()) {
      int startPosition = codeMatcher.start();
      int endPosition = codeMatcher.end();
      int startLine = getLineNumber(charToLineMapItem, startPosition);
      int endLine = getLineNumber(charToLineMapItem, endPosition);
      if (startLine > endLine) {
        log.info(
            "Code range discarded: start line ({}) greater than end line ({}).\ncodeMatcher: {}.\n"
                + "diffCode: {}",
            startLine,
            endLine,
            codeMatcher,
            diffCode);
        continue;
      }
      int startCharacter = getLineCharacter(diffCode, startPosition);
      int endCharacter = getLineCharacter(diffCode, endPosition);
      if (startLine == endLine && startCharacter > endCharacter) {
        log.info(
            "Code range discarded: start char ({}) greater than end char ({}) for line {}.\n"
                + "codeMatcher: {}.\n"
                + "diffCode: {}",
            startCharacter,
            endCharacter,
            startLine,
            codeMatcher,
            diffCode);
        continue;
      }
      currentCodeRange =
          GerritCodeRange.builder()
              .startLine(startLine)
              .endLine(endLine)
              .startCharacter(startCharacter)
              .endCharacter(endCharacter)
              .build();
      // If multiple commented code portions are found and currentCommentRange is closer to the line
      // number suggested by AIChat than closestCommentRange, it becomes the new
      // closestCommentRange
      if (closestCodeRange == null
          || calcCodeDistance(currentCodeRange, commentedLine)
              < calcCodeDistance(closestCodeRange, commentedLine)) {
        closestCodeRange = currentCodeRange.toBuilder().build();
      }
    }
  }
}
