"""Writes one standalone CurseForge changelog per uploaded file into release/changelogs/<version>/, from CHANGELOG.md.

Each file gets the release's section with the lines that don't apply to its build taken out, and a line saying which
integrations that build has. Earlier versions' files stay as they were uploaded.
"""
import os
import re

VERSION = '1.4.0'
ROOT = os.path.join(os.path.dirname(__file__), '..')
OUT = os.path.join(ROOT, 'release', 'changelogs', VERSION)

# (loader, minecraft, integrations)
BUILDS = [
    ('Fabric', '1.20.1', ['Jade', 'JEI', 'FTB Chunks', 'Open Parties and Claims', 'FTB Ranks', 'JourneyMap', "Xaero's Minimap", 'Antique Atlas', 'Create', 'KubeJS', 'Mod Menu']),
    ('Forge', '1.20.1', ['Jade', 'JEI', 'FTB Chunks', 'Open Parties and Claims', 'FTB Ranks', 'JourneyMap', "Xaero's Minimap", 'Create', 'KubeJS', 'GameStages', 'TerraFirmaCraft']),
    ('Fabric', '1.21.1', ['Jade', 'JEI', 'FTB Chunks', 'Open Parties and Claims', 'FTB Ranks', 'JourneyMap', "Xaero's Minimap", 'Antique Atlas', 'Mod Menu']),
    ('NeoForge', '1.21.1', ['Jade', 'JEI', 'FTB Chunks', 'Open Parties and Claims', 'FTB Ranks', 'JourneyMap', "Xaero's Minimap", 'Create', 'KubeJS', 'TerraFirmaCraft']),
    ('Forge', '1.21.1', ['JEI', 'Open Parties and Claims', 'JourneyMap', "Xaero's Minimap"]),
    ('Fabric', '1.21.11', ['Jade', 'JEI', 'Open Parties and Claims', 'FTB Ranks', 'JourneyMap', "Xaero's Minimap", 'Mod Menu']),
    ('NeoForge', '1.21.11', ['Jade', 'JEI', 'Open Parties and Claims', 'FTB Ranks', 'JourneyMap', "Xaero's Minimap"]),
    ('Forge', '1.21.11', ['Open Parties and Claims', 'JourneyMap', "Xaero's Minimap"]),
    ('Fabric', '26.2', ['Jade', 'JEI', 'Open Parties and Claims', 'FTB Ranks', 'JourneyMap', "Xaero's Minimap", 'Mod Menu']),
    ('NeoForge', '26.2', ['Jade', 'JEI', 'Open Parties and Claims', 'FTB Ranks', 'JourneyMap', "Xaero's Minimap"]),
    ('Forge', '26.2', ['Open Parties and Claims', 'JourneyMap', "Xaero's Minimap"]),
    ('Fabric', '26.3', ['Jade', 'JEI', 'Open Parties and Claims', 'FTB Ranks', 'JourneyMap', "Xaero's Minimap", 'Mod Menu']),
    ('NeoForge', '26.3', ['Jade', 'JEI', 'Open Parties and Claims', 'FTB Ranks', 'JourneyMap', "Xaero's Minimap"]),
    ('Forge', '26.3', ['Open Parties and Claims', 'JourneyMap', "Xaero's Minimap"]),
]


def section():
    text = open(os.path.join(ROOT, 'CHANGELOG.md'), encoding='utf-8').read()
    start = text.index(f'## {VERSION}')
    end = text.index('\n## ', start + 1)
    return text[start:end].rstrip() + '\n'


def for_build(loader, minecraft, integrations):
    s = section()
    if '### What exists on which build' in s:
        s = s[:s.index('### What exists on which build')].rstrip() + '\n'
    if VERSION == '1.2.1' and minecraft.startswith('26.'):
        title = s.splitlines()[0]
        return (f"{title}\n\nNothing changes on this build: Luki's Grand Capitals, the reason for 1.2.1, isn't out for Minecraft\n"
                f"{minecraft}. This file keeps every build on the same version. 1.2.0 and 1.2.1 can play together.\n")
    if 'KubeJS' not in integrations:
        s = re.sub(r"- \*\*KubeJS\*\*:[^\n]*\n", '', s)
        s = s.replace('a Java API with events, KubeJS support,', 'a Java API with events,')
    if 'GameStages' not in integrations:
        s = s.replace(', a\n  permission or a GameStages stage, or change their fare', ' or a\n  permission, or change their fare')
    if 'FTB Ranks' not in integrations:
        s = re.sub(r"- \*\*FTB Ranks\*\*[^\n]*\n(  [^\n]*\n)*", '', s)
    if 'FTB Chunks' not in integrations:
        s = s.replace(" a claim you couldn't build in, like FTB Chunks.", " a claim you couldn't build in.")
    if 'Antique Atlas' not in integrations:
        s = re.sub(r"\*\*Antique Atlas\*\*.*?\n\n", '', s, flags=re.S)
    if 'Create' not in integrations:
        s = re.sub(r"\n\*\*Create\*\*.*?(?=\n\n|\Z)", '', s, flags=re.S)
    if loader == 'Fabric':
        s = s.replace('only drew a coach while its middle block was on screen.',
                      'only drew a coach while its middle block was on screen; Fabric\n  was never affected.')
        s = s.replace('through LuckPerms or any other\n  permissions mod;', 'through LuckPerms or any other\n  permissions mod (fabric-permissions-api);')
    on_this = ', '.join(integrations[:-1]) + ' and ' + integrations[-1]
    s += f'\n### On this build ({loader} {minecraft})\nWorks with {on_this} when they are installed; none of them is required.\n'
    return s


def main():
    os.makedirs(OUT, exist_ok=True)
    for loader, minecraft, integrations in BUILDS:
        path = os.path.join(OUT, f'{loader.lower()}-{minecraft}.md')
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(for_build(loader, minecraft, integrations))
    print(f'wrote {len(BUILDS)} changelogs to {os.path.normpath(OUT)}')


if __name__ == '__main__':
    main()
