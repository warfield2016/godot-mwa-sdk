#!/usr/bin/env python
# SConstruct for building the SolanaMWA GDExtension C++ library
# Cross-compiles to Android arm64-v8a using godot-cpp at godot-4.3-stable tag

import os
import sys

if not os.path.isdir("godot-cpp"):
    print("ERROR: godot-cpp submodule not found. Run: git submodule update --init --recursive")
    Exit(1)

env = SConscript("godot-cpp/SConstruct")

# Project-specific build configuration
env.Append(CPPPATH=["src/"])
env.Append(CPPDEFINES=["GODOT_MWA_VERSION=\\\"0.1.0\\\""])

# Gather source files
sources = Glob("src/*.cpp")

# Build output filename matches the .gdextension library path:
# res://addons/SolanaMWA/bin/<target>/<android_abi>/libSolanaMWA.so
#
# Godot-internal arch names map to Android NDK ABI directory names as follows:
ANDROID_ABI_MAP = {
    "arm64":  "arm64-v8a",
    "arm32":  "armeabi-v7a",
    "x86_64": "x86_64",
    "x86_32": "x86",
}

if env["platform"] == "android":
    library_name = "libSolanaMWA.so"
    abi_dir = ANDROID_ABI_MAP.get(env["arch"])
    if abi_dir is None:
        print("ERROR: Unknown Android arch '{}'. Supported: {}".format(
            env["arch"], list(ANDROID_ABI_MAP.keys())))
        Exit(1)
    output_dir = "addons/SolanaMWA/bin/{}/{}/".format(
        "release" if env["target"] == "template_release" else "debug",
        abi_dir,
    )
else:
    # Fallback for desktop builds (will produce stubs)
    library_name = "libSolanaMWA{}{}".format(env["suffix"], env["SHLIBSUFFIX"])
    output_dir = "addons/SolanaMWA/bin/{}/".format(env["platform"])

library = env.SharedLibrary(
    target=output_dir + library_name,
    source=sources,
)

Default(library)
