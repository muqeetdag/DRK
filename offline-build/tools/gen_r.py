#!/usr/bin/env python3
"""Converts an aapt2 symbol table (R.txt) into a Kotlin R class.

Kotlinc cannot emit classes for .java sources, so instead of aapt2's
--java output we generate R.kt and compile it together with the app.

Usage: gen_r.py <symbols.txt> <package> <out/R.kt>
"""
import re
import sys

def main():
    symbols_path, package, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
    groups = {}
    with open(symbols_path) as f:
        for line in f:
            m = re.match(r"int\s+(\w+)\s+([\w.\[\]]+)\s+(0x[0-9a-fA-F]+)", line.strip())
            if not m:
                continue  # skip int[] styleables etc.
            t, name, value = m.groups()
            name = name.replace(".", "_")
            groups.setdefault(t, []).append((name, value))

    out = ["package %s" % package, "",
           "/** Generated from the aapt2 symbol table — do not edit. */",
           "object R {"]
    for t in sorted(groups):
        out.append("    object %s {" % t)
        for name, value in groups[t]:
            out.append("        const val %s: Int = %s" % (name, value))
        out.append("    }")
    out.append("}")
    with open(out_path, "w") as f:
        f.write("\n".join(out) + "\n")
    print("R.kt written: %d resource types" % len(groups))

if __name__ == "__main__":
    main()
