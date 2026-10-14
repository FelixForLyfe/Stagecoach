package net.feelix.stagecoach.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.feelix.stagecoach.client.screen.ClientConfigScreen;

/** Only loaded by Mod Menu itself, through the "modmenu" entrypoint. */
public class StagecoachModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClientConfigScreen::new;
    }
}
