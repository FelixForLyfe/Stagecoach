"""Counts the wood blocks in the pieces of each template pool make_injections.py targets, to pick each village's wood.

usage: python tools/count_village_woods.py <jar or zip>...
Pass the Minecraft jar and every structure mod's jar; prints one line per pool: the winning wood and the counts.
"""
import collections
import gzip
import io
import json
import sys
import zipfile

import nbtlib

import make_injections

WOODS = ["dark_oak", "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped"]
SHAPES = ("planks", "log", "wood", "stairs", "slab", "fence", "fence_gate", "door", "trapdoor", "stem", "hyphae", "block", "mosaic",
          "mosaic_stairs", "mosaic_slab")


def wood_of(block):
    path = block.split(":")[-1].removeprefix("stripped_")
    if path.startswith("pale_oak_"):
        return None
    for wood in WOODS:
        if path.startswith(wood + "_") and path[len(wood) + 1:] in SHAPES:
            return wood
    return None


def main():
    archives = [zipfile.ZipFile(p) for p in sys.argv[1:]]
    names = {}
    for z in archives:
        for n in z.namelist():
            names.setdefault(n, z)

    def read(path):
        z = names.get(path)
        return z.read(path) if z else None

    def pool_json(pool_id):
        ns, path = pool_id.split(":")
        raw = read(f"data/{ns}/worldgen/template_pool/{path}.json")
        return json.loads(raw) if raw else None

    def locations(element):
        kind = element.get("element_type", "")
        if kind.endswith("list_pool_element"):
            for e in element["elements"]:
                yield from locations(e)
        elif "location" in element:
            yield element["location"]

    def count(location):
        ns, path = location.split(":") if ":" in location else ("minecraft", location)
        raw = read(f"data/{ns}/structures/{path}.nbt") or read(f"data/{ns}/structure/{path}.nbt")
        if raw is None:
            return collections.Counter()
        nbt = nbtlib.File.parse(io.BytesIO(gzip.decompress(raw)))
        palette = nbt["palettes"][0] if "palettes" in nbt else nbt["palette"]
        woods = [wood_of(str(state.get("Name", ""))) for state in palette]
        out = collections.Counter()
        for block in nbt["blocks"]:
            wood = woods[int(block["state"])]
            if wood:
                out[wood] += 1
        return out

    for _, _, pools, _, _ in make_injections.POOLS:
        total, found = collections.Counter(), False
        for pool in pools:
            data = pool_json(pool)
            if data is None:
                continue
            found = True
            for entry in data["elements"]:
                for location in locations(entry["element"]):
                    total += count(location)
        if not found:
            print(f"{pools[0]}: not in these jars")
            continue
        best = total.most_common(1)[0][0] if total else "-"
        print(f"{pools[0]}: {best}  {dict(total.most_common(4))}")


if __name__ == "__main__":
    main()
