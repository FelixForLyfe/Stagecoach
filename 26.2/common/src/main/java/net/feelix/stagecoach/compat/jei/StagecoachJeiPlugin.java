package net.feelix.stagecoach.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@JeiPlugin // Forge finds JEI plugins by this annotation; Fabric by the "jei_mod_plugin" entrypoint
public class StagecoachJeiPlugin implements IModPlugin {
    private static @Nullable IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return Stagecoach.id("jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<ItemStack> coaches = StagecoachRegistry.ITEMS.values().stream().map(ItemStack::new).toList();
        registration.addItemStackInfo(coaches,
                Stagecoach.text("jei.info.what"),
                Stagecoach.text("jei.info.place"),
                Stagecoach.text("jei.info.discover"),
                Stagecoach.text("jei.info.travel"),
                Stagecoach.text("jei.info.break"));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Used by the dev screenshot harness to open the info page. */
    public static @Nullable IJeiRuntime runtime() {
        return runtime;
    }
}
