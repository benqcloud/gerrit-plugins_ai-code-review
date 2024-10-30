# Build

The @PLUGIN@ plugin can be built in-tree in Gerrit's `/plugins` path.

The `plugins/external_plugin_deps.bzl` file will need to be updated to match
or contain `@PLUGIN@/external_plugin_deps.bzl`.

```
git clone --recursive https://gerrit.googlesource.com/gerrit
cd gerrit
git clone "https://gerrit.googlesource.com/plugins/@PLUGIN@" plugins/@PLUGIN@
ln -sf plugins/@PLUGIN@/external_plugin_deps.bzl plugins/.
bazelisk build plugins/@PLUGIN@
```

The output is created in

```
gerrit/bazel-bin/plugins/@PLUGIN@/@PLUGIN@.jar
```

## Eclipse project setup

This project can be imported into the Eclipse IDE:

- Add the plugin name to the `CUSTOM_PLUGINS_TEST_DEPS`
set in Gerrit core in `tools/bzl/plugins.bzl`,
- execute:

```
./tools/eclipse/project.py
```

## Run tests

Run

```
bazelisk test plugins/@PLUGIN@/...
```

[Back to @PLUGIN@ documentation index][index]

[index]: index.html
