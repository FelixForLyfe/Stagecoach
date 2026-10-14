"""Writes the public event classes and the internal EventHooks for every loader of a version folder.

usage: python tools/make_api_events.py <version folder>   (e.g. 1.20.1)

The events have the same names, constructors and methods on every loader; only the base class and the way they are
posted differ. Common code builds them and posts them through net.feelix.stagecoach.platform.EventHooks.
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

# folder -> {loader: (event style, mappings, KubeJS forwarding)}
BUILDS = {
    '1.20.1': {'fabric': ('fabric', 'yarn', True), 'forge': ('forge6', 'yarn', True)},
    '1.21.1': {'fabric': ('fabric', 'yarn', False), 'neoforge': ('neoforge', 'yarn', True), 'forge': ('forge6', 'yarn', False)},
    '1.21.11': {'fabric': ('fabric', 'mojang', False), 'neoforge': ('neoforge', 'mojang', False), 'forge': ('forge7', 'mojang', False)},
    '26.2': {'fabric': ('fabric', 'mojang', False), 'neoforge': ('neoforge', 'mojang', False), 'forge': ('forge7', 'mojang', False)},
    '26.3': {'fabric': ('fabric', 'mojang', False), 'neoforge': ('neoforge', 'mojang', False), 'forge': ('forge7', 'mojang', False)},
}

TYPES = {
    'yarn': {
        'player': ('net.minecraft.server.network.ServerPlayerEntity', 'ServerPlayerEntity'),
        'world': ('net.minecraft.server.world.ServerWorld', 'ServerWorld'),
        'text': ('net.minecraft.text.Text', 'Text'),
        'pos': ('net.minecraft.util.math.BlockPos', 'BlockPos'),
        'dir': ('net.minecraft.util.math.Direction', 'Direction'),
        'worldName': 'World',
    },
    'mojang': {
        'player': ('net.minecraft.server.level.ServerPlayer', 'ServerPlayer'),
        'world': ('net.minecraft.server.level.ServerLevel', 'ServerLevel'),
        'text': ('net.minecraft.network.chat.Component', 'Component'),
        'pos': ('net.minecraft.core.BlockPos', 'BlockPos'),
        'dir': ('net.minecraft.core.Direction', 'Direction'),
        'worldName': 'Level',
    },
}
COMMON = {
    'stop': ('net.feelix.stagecoach.api.StopView', 'StopView'),
    'reason': ('net.feelix.stagecoach.api.EndReason', 'EndReason'),
    'destinations': ('java.util.List', 'List<Destination>'),
    'destination': ('net.feelix.stagecoach.api.Destination', None),
    'nullable': ('org.jetbrains.annotations.Nullable', None),
}

KUBEJS_NAMES = {'JourneyPreStart': 'preJourney', 'JourneyStarted': 'journeyStarted', 'JourneyCompleted': 'journeyCompleted',
                'JourneyEnded': 'journeyEnded', 'StopDiscover': 'stopDiscover', 'CoachPlace': 'coachPlace', 'CoachBreak': 'coachBreak',
                'DestinationList': 'destinationList'}

# (field, kind, mutable, nullable)
EVENTS = [
    ('JourneyPreStart', 'After every built-in check passed and before anything is paid; cancel to refuse with the fail message.',
     True, [('player', 'player', False, False), ('from', 'stop', False, True), ('to', 'stop', False, False),
            ('cost', 'int', True, False), ('itemFare', 'boolean', False, False), ('travelSeconds', 'int', True, False),
            ('instant', 'boolean', False, False), ('failMessage', 'text', True, True)]),
    ('JourneyStarted', 'After the fare was taken and the journey saved; {@code from} is null for a journey sent from anywhere.',
     False, [('player', 'player', False, False), ('from', 'stop', False, True), ('to', 'stop', False, False),
             ('cost', 'int', False, False), ('itemFare', 'boolean', False, False), ('travelSeconds', 'int', False, False),
             ('instant', 'boolean', False, False)]),
    ('JourneyCompleted', 'After the player was set down at the destination.',
     False, [('player', 'player', False, False), ('from', 'stop', False, True), ('to', 'stop', False, False),
             ('distance', 'int', False, False)]),
    ('JourneyEnded', 'Exactly once per journey, however it ended.',
     False, [('player', 'player', False, False), ('from', 'stop', False, True), ('to', 'stop', False, False),
             ('reason', 'reason', False, False)]),
    ('StopDiscover', 'Before a player discovers a stop; cancel to keep it unknown to them.',
     True, [('player', 'player', False, False), ('stop', 'stop', False, False)]),
    ('CoachPlace', 'Before a coach is placed by a player or by a command (then the player is null); cancel to refuse.',
     True, [('player', 'player', False, True), ('world', 'world', False, False), ('pos', 'pos', False, False),
            ('facing', 'dir', False, False), ('wood', 'String', False, False), ('failMessage', 'text', True, True)]),
    ('CoachBreak', 'Before a player takes a coach down; cancel to refuse.',
     True, [('player', 'player', False, False), ('stop', 'stop', False, False), ('failMessage', 'text', True, True)]),
    ('DestinationList', 'Before a player sees their destinations: add, remove, reorder or replace entries. Only OK entries can be travelled to.',
     False, [('player', 'player', False, False), ('here', 'stop', False, True), ('destinations', 'destinations', False, False)]),
]


def java_type(kind, types):
    if kind in ('int', 'boolean', 'String'):
        return kind
    if kind in types:
        return types[kind][1]
    return COMMON[kind][1]


def getter(field, kind, types):
    if kind == 'boolean':
        return 'is' + field[0].upper() + field[1:]
    return 'get' + field[0].upper() + field[1:]


def event_class(name, doc, cancellable, fields, style, mapping):
    types = TYPES[mapping]
    # the field takes each mapping's own name: world on Yarn, level on Mojang
    fields = [(types['worldName'].lower() if f == 'world' else f, k, m, n) for f, k, m, n in fields]
    cls = name + 'Event'
    imports = set()
    for _, kind, _, nullable in fields:
        if kind in types:
            imports.add(types[kind][0])
        elif kind in COMMON:
            imports.add(COMMON[kind][0])
            if kind == 'destinations':
                imports.add(COMMON['destination'][0])
        if nullable:
            imports.add(COMMON['nullable'][0])
    lines = []
    header = []
    if style == 'fabric':
        imports.update(['net.fabricmc.fabric.api.event.Event', 'net.fabricmc.fabric.api.event.EventFactory'])
        header.append(f'public class {cls} {{')
    elif style == 'forge6':
        imports.add('net.minecraftforge.eventbus.api.Event')
        if cancellable:
            imports.add('net.minecraftforge.eventbus.api.Cancelable')
            header.append('@Cancelable')
        header.append(f'public class {cls} extends Event {{')
    elif style == 'neoforge':
        imports.add('net.neoforged.bus.api.Event')
        if cancellable:
            imports.add('net.neoforged.bus.api.ICancellableEvent')
            header.append(f'public class {cls} extends Event implements ICancellableEvent {{')
        else:
            header.append(f'public class {cls} extends Event {{')
    elif style == 'forge7':
        imports.add('net.minecraftforge.eventbus.api.bus.EventBus')
        imports.add('net.minecraftforge.eventbus.api.event.MutableEvent')
        if cancellable:
            imports.add('net.minecraftforge.eventbus.api.bus.CancellableEventBus')
            imports.add('net.minecraftforge.eventbus.api.event.characteristic.Cancellable')
            header.append(f'public class {cls} extends MutableEvent implements Cancellable {{')
        else:
            header.append(f'public class {cls} extends MutableEvent {{')
    lines.append('package net.feelix.stagecoach.api.event;')
    lines.append('')
    mc = sorted(i for i in imports if not i.startswith('java.'))
    jv = sorted(i for i in imports if i.startswith('java.'))
    lines += [f'import {i};' for i in mc]
    if jv:
        lines.append('')
        lines += [f'import {i};' for i in jv]
    lines.append('')
    lines.append(f'/** {doc} */')
    lines += header
    if style == 'fabric':
        listener = 'on' + name
        lines.append(f'    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> {{')
        lines.append(f'        for (Listener listener : listeners) listener.{listener}(event);')
        lines.append('    });')
        lines.append('')
        lines.append('    @FunctionalInterface')
        lines.append('    public interface Listener {')
        lines.append(f'        void {listener}({cls} event);')
        lines.append('    }')
        lines.append('')
    if style == 'forge7':
        bus = 'CancellableEventBus' if cancellable else 'EventBus'
        lines.append(f'    public static final {bus}<{cls}> BUS = {bus}.create({cls}.class);')
        lines.append('')
    for field, kind, mutable, nullable in fields:
        t = java_type(kind, types)
        ann = '@Nullable ' if nullable else ''
        final = '' if mutable else 'final '
        lines.append(f'    private {final}{ann}{t} {field};')
    if cancellable and style in ('fabric',):
        lines.append('    private boolean canceled;')
    if cancellable and style == 'forge7':
        lines.append('    private boolean canceled;')
    lines.append('')
    params = []
    for field, kind, mutable, nullable in fields:
        ann = '@Nullable ' if nullable else ''
        params.append(f'{ann}{java_type(kind, types)} {field}')
    sig = f'    public {cls}(' + ', '.join(params) + ') {'
    if len(sig) > 120:
        lines.append(f'    public {cls}(' + ', '.join(params[:len(params) // 2 + 1]) + ',')
        lines.append('            ' + ', '.join(params[len(params) // 2 + 1:]) + ') {')
    else:
        lines.append(sig)
    for field, _, _, _ in fields:
        lines.append(f'        this.{field} = {field};')
    lines.append('    }')
    for field, kind, mutable, nullable in fields:
        t = java_type(kind, types)
        ann = '@Nullable ' if nullable else ''
        lines.append('')
        lines.append(f'    public {ann}{t} {getter(field, kind, types)}() {{')
        lines.append(f'        return {field};')
        lines.append('    }')
        if mutable:
            setter = 'set' + field[0].upper() + field[1:]
            lines.append('')
            lines.append(f'    public void {setter}({ann}{t} {field}) {{')
            lines.append(f'        this.{field} = {field};')
            lines.append('    }')
    if cancellable and style in ('fabric', 'forge7'):
        lines.append('')
        lines.append('    public boolean isCanceled() {')
        lines.append('        return canceled;')
        lines.append('    }')
        lines.append('')
        lines.append('    public void setCanceled(boolean canceled) {')
        lines.append('        this.canceled = canceled;')
        lines.append('    }')
    lines.append('}')
    lines.append('')
    return '\n'.join(lines)


def hooks_class(style, kubejs):
    lines = ['package net.feelix.stagecoach.platform;', '']
    imports = [f'net.feelix.stagecoach.api.event.{n}Event' for n, *_ in EVENTS]
    if kubejs:
        imports.append('net.feelix.stagecoach.compat.KubeJs')
    if style == 'forge6':
        imports.append('net.minecraftforge.common.MinecraftForge')
    elif style == 'neoforge':
        imports.append('net.neoforged.neoforge.common.NeoForge')
    lines += [f'import {i};' for i in sorted(imports)]
    lines += ['', '/** True when a listener cancelled the event. */', 'public final class EventHooks {', '    private EventHooks() {}']
    for name, _, cancellable, _ in EVENTS:
        cls = name + 'Event'
        lines.append('')
        lines.append(f'    public static boolean post({cls} event) {{')
        if style == 'fabric':
            lines.append(f'        {cls}.EVENT.invoker().on{name}(event);')
            native = 'event.isCanceled()' if cancellable else 'false'
        elif style == 'forge6':
            native = 'MinecraftForge.EVENT_BUS.post(event)'
        elif style == 'neoforge':
            lines.append('        NeoForge.EVENT_BUS.post(event);')
            native = 'event.isCanceled()' if cancellable else 'false'
        else:
            native = f'{cls}.BUS.post(event) || event.isCanceled()' if cancellable else None
            if native is None:
                lines.append(f'        {cls}.BUS.post(event);')
                native = 'false'
        if kubejs and cancellable:
            lines.append(f'        boolean cancelled = {native};')
            lines.append(f'        if (KubeJs.post("{KUBEJS_NAMES[name]}", event)) {{')
            lines.append('            event.setCanceled(true);')
            lines.append('            cancelled = true;')
            lines.append('        }')
            lines.append('        return cancelled;')
        else:
            if style == 'forge6' and not cancellable:
                lines.append('        MinecraftForge.EVENT_BUS.post(event);')
                native = 'false'
            if kubejs:
                lines.append(f'        KubeJs.post("{KUBEJS_NAMES[name]}", event);')
            lines.append(f'        return {native};')
        lines.append('    }')
    lines += ['}', '']
    return '\n'.join(lines)


def main():
    folder = sys.argv[1]
    for loader, (style, mapping, kubejs) in BUILDS[folder].items():
        base = ROOT / folder / loader / 'src/main/java/net/feelix/stagecoach'
        events = base / 'api/event'
        events.mkdir(parents=True, exist_ok=True)
        for name, doc, cancellable, fields in EVENTS:
            (events / f'{name}Event.java').write_text(event_class(name, doc, cancellable, fields, style, mapping),
                                                       encoding='utf-8', newline='\n')
        (base / 'platform').mkdir(parents=True, exist_ok=True)
        (base / 'platform/EventHooks.java').write_text(hooks_class(style, kubejs), encoding='utf-8', newline='\n')
        print(f'{folder}/{loader}: {len(EVENTS)} events ({style}, {mapping})')


if __name__ == '__main__':
    main()
