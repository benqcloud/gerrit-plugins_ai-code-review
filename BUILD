load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "ai-code-review",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: ai-code-review",
        "Gerrit-Module: com.googlesource.gerrit.plugins.aicodereview.Module",
        "Implementation-Title: AI Code Review Gerrit Plugin",
        "Implementation-URL: https://gerrit.googlesource.com/plugins/ai-code-review",
        "Implementation-Vendor: OpenSource Gerrit Plugin",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        "@logback//jar",
        "@lombok//jar",
        "@okhttp//jar",
        "@slf4j//jar",
    ],
)

junit_tests(
    name = "ai-code-review_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    javacopts = ["-Xep:DoNotMock:OFF"],
    resources = glob(["src/test/resources/**/*"]),
    tags = [
        "ai-code-review",
        "local",
    ],
    deps = [
        ":ai-code-review__plugin_test_deps",
    ],
)

java_library(
    name = "ai-code-review__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":ai-code-review__plugin",
        ":lombok-java",
        "@mockito-inline//jar",
        "@wiremock//jar",
    ],
)
