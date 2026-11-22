#!/usr/bin/env python3
"""NYX white-label configurator.

Reads white-label.json and rewrites the buildable source copy so that
name, tagline, palette, feature defaults and deep links are baked in.
Keeps __TOKEN__ placeholders in source; outputs _work/config.json that
build.sh also uses for versionCode / keystore metadata.
"""
import json
import os
import re
import sys

DEFAULT_GRAVITY = {"x": 0.5, "y": 0.44, "size": 0.58}


def fail(msg: str) -> "None":
    print(f"\033[31m✗ white-label.json: {msg}\033[0m", file=sys.stderr)
    sys.exit(1)


def load_config(path: str) -> dict:
    try:
        with open(path, encoding="utf-8") as fh:
            cfg = json.load(fh)
    except FileNotFoundError:
        fail(f"config file not found: {path}")
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON ({exc})")

    out = {
        "appName": str(cfg.get("appName", "NYX")).strip()[:24] or "NYX",
        "tagline": str(cfg.get("tagline", "Instagram, reimagined."))[:80],
        "flavor": re.sub(r"[^a-z0-9_-]", "", str(cfg.get("flavor", "obsidian")).lower()) or "obsidian",
        "version": str(cfg.get("version", "1.0.0")),
        "versionCode": int(cfg.get("versionCode", 100)),
        "accent": str(cfg.get("accent", "#7C5CFF")),
        "secondary": str(cfg.get("secondary", "#22D3EE")),
        "iconStyle": str(cfg.get("iconStyle", "glyph")),
        "glyph": str(cfg.get("glyph", "orbit")),
        "signature": str(cfg.get("signature", ""))[:120],
        "creditDev": str(cfg.get("credits", {}).get("developer", "DRK")),
        "creditHandle": str(cfg.get("credits", {}).get("telegram", "@DRKMUQEETT")),
        "links": {
            "telegram": str(cfg.get("links", {}).get("telegram", "")),
            "github": str(cfg.get("links", {}).get("github", "")),
        },
        "defaults": {
            "floatingNav": bool(cfg.get("defaults", {}).get("floatingNav", True)),
            "reelsDownloader": bool(cfg.get("defaults", {}).get("reelsDownloader", True)),
            "storiesDownloader": bool(cfg.get("defaults", {}).get("storiesDownloader", True)),
            "feedDownloader": bool(cfg.get("defaults", {}).get("feedDownloader", True)),
            "downloadFolder": str(cfg.get("defaults", {}).get("downloadFolder", "NYX"))[:32],
        },
        "scope": [str(pkg) for pkg in cfg.get("scope", DEFAULT_SCOPE) if pkg],
        "installMode": str(cfg.get("installMode", "both")),
        "releaseChannel": str(cfg.get("releaseChannel", "stable")),
        "keyAlias": str(cfg.get("keyAlias", "nyx")),
        "storePassword": str(cfg.get("storePassword", "nyx-white-label")),
    }
    # 6-digit only: the template prepends FF. A bare 6-digit value written straight
    # into an int constant silently becomes a fully transparent colour.
    if not re.match(r"^#[0-9a-fA-F]{6}$", out["accent"]):
        fail(f"accent must be #RRGGBB, got {out['accent']!r}")
    if not re.match(r"^#[0-9a-fA-F]{6}$", out["secondary"]):
        fail(f"secondary must be #RRGGBB, got {out['secondary']!r}")
    return out


# The hosts we hook. Instagram is the primary host; Threads shares the same
# medias/Clips internals and is what users expect a companion module to name.
# The module's own package is last: managers derive a legacy module's self-hook
# row from the declared scope, and naming it explicitly keeps that row present.
DEFAULT_SCOPE = [
    "com.instagram.android",
    "com.instagram.android",
    "com.instagram.barcelona",
    "io.nyx.app",
]


def scope_packages(cfg: dict) -> list[str]:
    """De-duplicated, order-preserving host list."""
    seen = set()
    out = []
    for pkg in cfg["scope"]:
        if pkg not in seen:
            seen.add(pkg)
            out.append(pkg)
    return out


def scope_items(cfg: dict) -> str:
    return "\n".join(f"        <item>{pkg}</item>" for pkg in scope_packages(cfg))


def scope_java(cfg: dict) -> str:
    """A Java array literal for the hook side."""
    pkgs = scope_packages(cfg)
    if not pkgs:
        pkgs = ["com.instagram.android"]
    return ", ".join(f'"{pkg}"' for pkg in pkgs)


TOKEN_RE = re.compile(r"__([A-Z0-9_]+)__")


def tokens_for(cfg: dict) -> dict:
    d = cfg["defaults"]
    return {
        "APP_NAME": cfg["appName"],
        "TAGLINE": cfg["tagline"],
        "FLAVOR": cfg["flavor"],
        "VERSION": cfg["version"],
        "VERSION_CODE": str(cfg["versionCode"]),
        "ACCENT": cfg["accent"],
        "SECONDARY": cfg["secondary"],
        "SIGNATURE": cfg["signature"],
        "CREDIT_DEV": cfg["creditDev"].replace('"', "'"),
        "CREDIT_HANDLE": cfg["creditHandle"].replace('"', "'"),
        "ACCENT_HEX": cfg["accent"][1:].upper(),
        "SECONDARY_HEX": cfg["secondary"][1:].upper(),
        "TELEGRAM": cfg["links"]["telegram"],
        "GITHUB": cfg["links"]["github"],
        "DEF_FLOATING": "true" if d["floatingNav"] else "false",
        "DEF_REELS": "true" if d["reelsDownloader"] else "false",
        "DEF_STORIES": "true" if d["storiesDownloader"] else "false",
        "DEF_FEED": "true" if d["feedDownloader"] else "false",
        "DEF_FOLDER": d["downloadFolder"],
        "CHANNEL": cfg["releaseChannel"],
        "SCOPE_ITEMS": scope_items(cfg),
        "SCOPE_JAVA": scope_java(cfg),
        "INSTALL_MODE": cfg["installMode"],
        "PKG_SUFFIX": cfg.get("packageSuffix", ""),
    }


def java_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


def xml_escape(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "\\'")
    )


def replace_tokens(path: str, tokens: dict) -> None:
    with open(path, encoding="utf-8") as fh:
        text = fh.read()

    def sub(match: re.Match) -> str:
        key = match.group(1)
        if key not in tokens:
            return match.group(0)
        return tokens[key]

    text = TOKEN_RE.sub(sub, text)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def write_theme(path: str, cfg: dict) -> None:
    d = cfg["defaults"]
    body = f"""
package io.nyx.app;

/** Generated by tools/configure.py — do not edit the work copy by hand. */
public final class NyxTheme {{
    private NyxTheme() {{}}

    public static final String APP_NAME = "{java_escape(cfg['appName'])}";
    public static final String TAGLINE = "{java_escape(cfg['tagline'])}";
    public static final String FLAVOR = "{java_escape(cfg['flavor'])}";
    public static final String VERSION = "{java_escape(cfg['version'])}";
    public static final String CHANNEL = "{java_escape(cfg['releaseChannel'])}";
    public static final String SIGNATURE = "{java_escape(cfg['signature'])}";
    public static final String TELEGRAM = "{java_escape(cfg['links']['telegram'])}";
    public static final String GITHUB = "{java_escape(cfg['links']['github'])}";
    public static final int ACCENT = 0xFF{cfg['accent'][1:]};
    public static final int SECONDARY = 0xFF{cfg['secondary'][1:]};

    public static final String DEF_FOLDER = "{java_escape(d['downloadFolder'])}";
    public static final boolean DEF_FLOATING = {str(d['floatingNav']).lower()};
    public static final boolean DEF_REELS = {str(d['reelsDownloader']).lower()};
    public static final boolean DEF_STORIES = {str(d['storiesDownloader']).lower()};
    public static final boolean DEF_FEED = {str(d['feedDownloader']).lower()};
}}
"""
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(body.lstrip("\n"))


def write_gen_settings(work_dir: str, cfg: dict) -> None:
    path = os.path.join(work_dir, "gen", "io", "nyx", "app", "GenSettings.java")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    d = cfg["defaults"]
    body = f"""
package io.nyx.app;

/** Generated by tools/configure.py */
public final class GenSettings {{
    private GenSettings() {{}}
    public static final boolean FLOATING = {str(d['floatingNav']).lower()};
    public static final boolean REELS = {str(d['reelsDownloader']).lower()};
    public static final boolean STORIES = {str(d['storiesDownloader']).lower()};
    public static final boolean FEED = {str(d['feedDownloader']).lower()};
    public static final String FOLDER = "{java_escape(d['downloadFolder'])}";
}}
"""
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(body.lstrip("\n"))


def main() -> None:
    argv = sys.argv[1:]
    apply_tokens = "--apply" in argv
    package_suffix = ""
    label = ""
    cleaned = []
    index = 0
    while index < len(argv):
        arg = argv[index]
        if arg == "--apply":
            index += 1
            continue
        if arg in ("--package-suffix", "--label"):
            value = argv[index + 1] if index + 1 < len(argv) else ""
            if arg == "--package-suffix":
                package_suffix = value
            else:
                label = value
            index += 2
            continue
        cleaned.append(arg)
        index += 1

    if len(cleaned) != 2:
        print("usage: configure.py <white-label.json> <repo-root> [--apply] "
              "[--package-suffix .lspatch] [--label nonroot]", file=sys.stderr)
        sys.exit(2)
    config_path, repo = cleaned[0], cleaned[1]
    cfg = load_config(config_path)
    # The repo argument is the variant work dir: .build/work-<variant>.
    work = repo if "work-" in os.path.basename(repo) else os.path.join(repo, ".build", "work")
    os.makedirs(work, exist_ok=True)
    with open(os.path.join(work, "config.json"), "w", encoding="utf-8") as fh:
        json.dump(cfg, fh, indent=2)

    tokens = tokens_for(cfg)
    # Token replacement happens on the work copy, not the checked-in source.
    app_src = os.path.join(work, "app-src")
    if not apply_tokens:
        return

    count = 0
    for base, _dirs, files in os.walk(app_src):
        for name in files:
            ext = os.path.splitext(name)[1].lower()
            if ext not in (".java", ".xml", ".html", ".js", ".css", ".json", ".md"):
                continue
            path = os.path.join(base, name)
            with open(path, encoding="utf-8") as fh:
                text = fh.read()
            if TOKEN_RE.search(text):
                replace_tokens(path, tokens)
                count += 1

    print(f"  tokens applied to {count} files")

    # ---------------------------------------------------------- variant naming
    manifest = os.path.join(app_src, "AndroidManifest.xml")
    if package_suffix:
        with open(manifest, encoding="utf-8") as fh:
            text = fh.read()
        text = re.sub(r'package="io\.nyx\.app"',
                      f'package="io.nyx.app{package_suffix}"', text, count=1)
        # The module's own package must be in the scope list for LSPosed to
        # report it as active - mirror the new id.
        scope_path = os.path.join(app_src, "res", "values", "colors.xml")
        with open(scope_path, encoding="utf-8") as fh:
            scope = fh.read()
        scope = scope.replace("<item>io.nyx.app</item>",
                              f"<item>io.nyx.app{package_suffix}</item>")
        with open(scope_path, "w", encoding="utf-8") as fh:
            fh.write(scope)
        with open(manifest, "w", encoding="utf-8") as fh:
            fh.write(text)
        # aapt2 generates R.java under the manifest package, so every source
        # import of io.nyx.app.R must follow the new id.
        for base, _dirs, files in os.walk(app_src):
            for name in files:
                if not name.endswith(".java"):
                    continue
                path = os.path.join(base, name)
                with open(path, encoding="utf-8") as fh:
                    source = fh.read()
                if "import io.nyx.app.R;" in source:
                    source = source.replace(
                        "import io.nyx.app.R;",
                        f"import io.nyx.app{package_suffix}.R;")
                    with open(path, "w", encoding="utf-8") as fh:
                        fh.write(source)
        print(f"  package id -> io.nyx.app{package_suffix}")

    cfg["outputLabel"] = label
    with open(os.path.join(work, "config.json"), "w", encoding="utf-8") as fh:
        json.dump(cfg, fh, indent=2)


if __name__ == "__main__":
    main()
