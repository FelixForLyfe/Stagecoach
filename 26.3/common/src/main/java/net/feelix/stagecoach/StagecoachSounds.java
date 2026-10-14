package net.feelix.stagecoach;

import net.feelix.stagecoach.platform.Registrar;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

/** Resource packs replace these through sounds.json; out of the box they play vanilla sounds. */
public final class StagecoachSounds {
    private StagecoachSounds() {}

    public static final SoundEvent JOURNEY_DEPART = SoundEvent.createVariableRangeEvent(Stagecoach.id("journey.depart"));
    public static final SoundEvent JOURNEY_ARRIVE = SoundEvent.createVariableRangeEvent(Stagecoach.id("journey.arrive"));
    public static final SoundEvent JOURNEY_LOOP = SoundEvent.createVariableRangeEvent(Stagecoach.id("journey.loop"));
    public static final SoundEvent STOP_DISCOVER = SoundEvent.createVariableRangeEvent(Stagecoach.id("stop.discover"));
    public static final SoundEvent COACH_PLACE = SoundEvent.createVariableRangeEvent(Stagecoach.id("coach.place"));

    static void register(Registrar r) {
        for (SoundEvent sound : new SoundEvent[] {JOURNEY_DEPART, JOURNEY_ARRIVE, JOURNEY_LOOP, STOP_DISCOVER, COACH_PLACE}) {
            r.register(Registries.SOUND_EVENT, sound.location(), () -> sound);
        }
    }
}
