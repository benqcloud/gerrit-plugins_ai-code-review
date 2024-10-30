load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "logback",
        artifact = "ch.qos.logback:logback-classic:1.2.11",
        sha1 = "4741689214e9d1e8408b206506cbe76d1c6a7d60",
    )

    maven_jar(
        name = "lombok",
        artifact = "org.projectlombok:lombok:1.18.24",
        sha1 = "13a394eed5c4f9efb2a6d956e2086f1d81e857d9",
    )

    maven_jar(
        name = "okhttp",
        artifact = "com.squareup.okhttp3:okhttp:4.1.0",
        sha1 = "9308be2b5f9174def7d4795f415b60f5632b9fb9",
    )

    maven_jar(
        name = "slf4j",
        artifact = "org.slf4j:slf4j-api:1.7.36",
        sha1 = "6c62681a2f655b49963a5983b8b0950a6120ae14",
    )

    maven_jar(
        name = "wiremock",
        artifact = "com.github.tomakehurst:wiremock-standalone:2.27.2",
        sha1 = "327647a19b2319af2526b9c33a5733a2241723e0",
    )

    maven_jar(
        name = "mockito-inline",
        artifact = "org.mockito:mockito-inline:4.5.1",
        sha1 = "3d1dffee9a8a1998ec782383ca2f818848f2d5f1",
    )
