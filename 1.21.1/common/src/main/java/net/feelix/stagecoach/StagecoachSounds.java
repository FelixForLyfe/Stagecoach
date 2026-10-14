package net.feelix.stagecoach;

import net.feelix.stagecoach.platform.Registrar;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvent;

/** Resource packs replace these through sounds.json; out of the box they play vanilla sounds. */
public final class StagecoachSounds {
    private StagecoachSounds() {}

    public static final SoundEvent JOURNEY_DEPART = SoundEvent.of(Stagecoach.id("journey.depart"));
    public static final SoundEvent JOURNEY_ARRIVE = SoundEvent.of(Stagecoach.id("journey.arrive"));
    public static final SoundEvent JOURNEY_LOOP = SoundEvent.of(Stagecoach.id("journey.loop"));
    public static final SoundEvent STOP_DISCOVER = SoundEvent.of(Stagecoach.id("stop.discover"));
    public static final SoundEvent COACH_PLACE = SoundEvent.of(Stagecoach.id("coach.place"));

    static void register(Registrar r) {
        for (SoundEvent sound : new SoundEvent[] {JOURNEY_DEPART, JOURNEY_ARRIVE, JOURNEY_LOOP, STOP_DISCOVER, COACH_PLACE}) {
            r.register(RegistryKeys.SOUND_EVENT, sound.getId(), () -> sound);
        }
    }
}
