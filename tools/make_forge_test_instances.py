"""Writes Forge 26.x's test-instance files, one per shared @GameTest (usage: <version folder>)."""
import json
import os
import re
import sys

folder = sys.argv[1]
tests = os.path.join(folder, 'common/src/gametest/java/net/feelix/stagecoach/gametest')
out = os.path.join(folder, 'forge/src/gametest/resources/data/stagecoach_gametest/test_instance')
os.makedirs(out, exist_ok=True)


def snake(name):
    return re.sub(r'([a-z0-9])([A-Z])', r'\1_\2', name).lower()


sources = {f[:-5]: open(os.path.join(tests, f), encoding='utf-8').read() for f in sorted(os.listdir(tests)) if f.endswith('.java')}
# test classes share structure names through static imports
constants = {k: v for src in sources.values() for k, v in re.findall(r'String (\w+) = "([^"]+)";', src)}

count = 0
for cls, src in sources.items():
    for args, method in re.findall(r'@GameTest\(([^)]*)\)\s*public void (\w+)\(', src):
        fields = dict(re.findall(r'(\w+)\s*=\s*([\w":/.]+)', args))
        structure = fields.get('structure', '"fabric-gametest-api-v1:empty"')
        structure = structure.strip('"') if structure.startswith('"') else constants[structure]
        test = {
            'type': 'minecraft:function',
            'function': f'stagecoach_gametest:{snake(cls)}_{snake(method)}',
            'environment': 'minecraft:default',
            'structure': structure,
            'max_ticks': int(fields.get('maxTicks', 20)),
            'padding': 1,
        }
        with open(os.path.join(out, f'{snake(cls)}_{snake(method)}.json'), 'w', encoding='utf-8') as f:
            f.write(json.dumps(test, indent=2) + '\n')
        count += 1
print(f'wrote {count} test instances to {out}')
