#!/usr/bin/env python3
"""Builds server/src/nativeMain/resources/s101/viewing_groups.json from the IHO catalogues.

S-57 class (njord source-layer)
  -> S-101 feature type            FeatureCatalogue.xml, <alias> holds the S-57 acronym
  -> S-101 viewing group           PortrayalCatalog/Rules/<FeatureType>.lua
  -> viewing group layer, display  portrayal_catalogue.xml, <viewingGroupLayers> / <displayModes>

Both catalogues are read at a pinned commit so the output is reproducible. Needs network access
to raw.githubusercontent.com and api.github.com, nothing else (standard library only).

    python3 s101/extract_viewing_groups.py
"""
import collections
import json
import os
import re
import urllib.request

PC_REPO = "iho-ohi/S-101_Portrayal-Catalogue"
PC_COMMIT = "152940b403f0d38b7237baa925427b5581a6c6e7"  # 2026-09-29
FC_REPO = "iho-ohi/S-101-Documentation-and-FC"
FC_COMMIT = "d0dcb87064bd3fefaaa13f93eb994bb5c93e8a8c"  # 2026-06-17
FC_PATH = "S-101FC/FeatureCatalogue.xml"

BASE_DIR = os.path.dirname(os.path.realpath(__file__))
OUT = os.path.join(BASE_DIR, "../server/src/nativeMain/resources/s101/viewing_groups.json")
S57_CLASSES = os.path.join(BASE_DIR, "../server/src/nativeMain/resources/s57objectclasses.json")

# S-57 classes the feature catalogue has no alias for (removed or remodelled in S-101)
MANUAL_FEATURES = {
    "TSELNE": ["SeparationZoneOrLine"],
    "TSEZNE": ["SeparationZoneOrLine"],
    "LAKSHR": ["Lake"],
    "MORFAC": ["MooringArea", "MooringBuoy"],
    "PLY": ["DataCoverage"],  # njord's own chart outline layer
}
# Classes whose S-101 portrayal comes from their parent feature's rule
MANUAL_VIEWING_GROUPS = {
    "TOPMAR": {"viewingGroup": 27010, "note": "topmarks are drawn with their buoy or beacon (TOPMAR02)"},
}
# Text groups the rules set outside AddTextInstruction
MANUAL_TEXT_VIEWING_GROUPS = {
    "DEPCNT": 90031,  # DEPCNT03: 'ViewingGroup:' .. viewingGroup .. ',90031' before the SAFCON labels
}


def fetch(repo, commit, path):
    url = f"https://raw.githubusercontent.com/{repo}/{commit}/{urllib.request.quote(path)}"
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read().decode("utf-8")


def list_rules():
    url = f"https://api.github.com/repos/{PC_REPO}/git/trees/{PC_COMMIT}?recursive=1"
    with urllib.request.urlopen(url, timeout=60) as r:
        tree = json.load(r)["tree"]
    return [t["path"] for t in tree if re.fullmatch(r"PortrayalCatalog/Rules/[^/]+\.lua", t["path"])]


def feature_aliases(fc_xml):
    aliases = collections.defaultdict(list)
    for block in re.findall(r"<S100FC:S100_FC_FeatureType[^>]*>(.*?)</S100FC:S100_FC_FeatureType>", fc_xml, re.S):
        code = re.search(r"<S100FC:code>(.*?)</S100FC:code>", block).group(1)
        for alias in re.findall(r"<S100FC:alias>(.*?)</S100FC:alias>", block):
            aliases[alias].append(code)
    return aliases


def rule_viewing_groups(lua):
    """Default viewing group of a rule, all the groups it can use, and its text viewing group."""
    default = re.search(r"local\s+viewingGroup\s*=\s*(\d+)", lua)
    literal = re.findall(r"ViewingGroup:(\d+)", lua) + re.findall(r"\bviewingGroup\s*=\s*(\d+)", lua)
    counts = collections.Counter(int(v) for v in literal if not v.startswith("9"))
    primary = int(default.group(1)) if default else (counts.most_common(1)[0][0] if counts else None)
    text = re.search(r"local\s+textViewingGroup\s*=\s*(\d+)", lua)
    text_groups = [int(g) for g in (text_call_argument(lua, i, 1) for i in text_calls(lua)) if g and g.isdigit()]
    text_vg = int(text.group(1)) if text else (collections.Counter(text_groups).most_common(1)[0][0] if text_groups else None)
    return primary, sorted(counts), text_vg


def text_calls(lua):
    return [m.end() for m in re.finditer(r"AddTextInstruction\(", lua)]


def text_call_argument(lua, start, index):
    """index-th argument of the call opening at start, splitting on top-level commas only."""
    depth, args, current = 0, [], ""
    for ch in lua[start:]:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            if depth == 0:
                args.append(current.strip())
                break
            depth -= 1
        elif ch == "," and depth == 0:
            args.append(current.strip())
            current = ""
            continue
        current += ch
    return args[index] if len(args) > index else None


def portrayal_tables(pc_xml):
    layers = []
    for m in re.finditer(r'<viewingGroupLayer id="([^"]+)">(.*?)</viewingGroupLayer>', pc_xml, re.S):
        layers.append({
            "id": m.group(1),
            "name": re.search(r"<name>(.*?)</name>", m.group(2)).group(1),
            "viewingGroups": [int(v) for v in re.findall(r"<viewingGroup>(\d+)</viewingGroup>", m.group(2))],
        })
    modes = []
    block = pc_xml[pc_xml.index("<displayModes>"):pc_xml.index("</displayModes>")]
    for m in re.finditer(r'<displayMode id="([^"]+)">(.*?)</displayMode>', block, re.S):
        modes.append({
            "id": m.group(1),
            "name": re.search(r"<name>(.*?)</name>", m.group(2)).group(1),
            "viewingGroupLayers": re.findall(r"<viewingGroupLayer>([^<]+)</viewingGroupLayer>", m.group(2)),
        })
    return layers, modes


def layer_and_mode(vg, layers, modes):
    """Most specific viewing group layer holding vg (Lights rather than Buoys, beacons, aids to
    navigation), and the first (smallest) display mode showing it."""
    holding = sorted((l for l in layers if vg in l["viewingGroups"]), key=lambda l: len(l["viewingGroups"]))
    layer = holding[0]["id"] if holding else None
    mode = next((m["id"] for m in modes if layer in m["viewingGroupLayers"]), None)
    return layer, mode


def main():
    pc_xml = fetch(PC_REPO, PC_COMMIT, "PortrayalCatalog/portrayal_catalogue.xml")
    fc_xml = fetch(FC_REPO, FC_COMMIT, FC_PATH)
    rules = {os.path.basename(p)[:-4]: fetch(PC_REPO, PC_COMMIT, p) for p in list_rules()}
    aliases = feature_aliases(fc_xml)
    layers, modes = portrayal_tables(pc_xml)

    classes, unmapped = {}, []
    # Upper-case acronyms only: the file also lists inland (lower-case) and meta ($TEXTS) classes
    s57_classes = [c for c in json.load(open(S57_CLASSES)) if re.fullmatch(r"[A-Z][A-Z_0-9]{4,5}", c)]
    for s57 in sorted(s57_classes) + sorted(MANUAL_FEATURES):
        if s57 in classes:
            continue
        if s57 in MANUAL_VIEWING_GROUPS:
            entry = {"features": [], **MANUAL_VIEWING_GROUPS[s57]}
            entry["viewingGroups"] = [entry["viewingGroup"]]
        else:
            features = MANUAL_FEATURES.get(s57) or aliases.get(s57)
            if not features:
                unmapped.append(s57)
                continue
            found = [rule_viewing_groups(rules[f]) for f in features if f in rules]
            primaries = [p for p, _, _ in found if p]
            if not primaries:
                unmapped.append(s57)
                continue
            entry = {
                "features": features,
                "viewingGroup": collections.Counter(primaries).most_common(1)[0][0],
                "viewingGroups": sorted({v for _, vs, _ in found for v in vs} | set(primaries)),
            }
            text_vgs = [t for _, _, t in found if t]
            if s57 in MANUAL_TEXT_VIEWING_GROUPS:
                entry["textViewingGroup"] = MANUAL_TEXT_VIEWING_GROUPS[s57]
            elif text_vgs:
                entry["textViewingGroup"] = text_vgs[0]
        entry["viewingGroupLayer"], entry["displayMode"] = layer_and_mode(entry["viewingGroup"], layers, modes)
        if "textViewingGroup" in entry:
            entry["textViewingGroupLayer"], _ = layer_and_mode(entry["textViewingGroup"], layers, modes)
        classes[s57] = entry

    out = {
        "source": {
            "portrayalCatalogue": {"repo": PC_REPO, "commit": PC_COMMIT},
            "featureCatalogue": {"repo": FC_REPO, "commit": FC_COMMIT, "path": FC_PATH},
        },
        "displayModes": modes,
        "viewingGroupLayers": layers,
        "classes": classes,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump(out, f, indent=1, ensure_ascii=False)
        f.write("\n")
    print(f"{len(classes)} classes mapped, {len(unmapped)} without S-101 viewing group: {' '.join(unmapped)}")


if __name__ == "__main__":
    main()
