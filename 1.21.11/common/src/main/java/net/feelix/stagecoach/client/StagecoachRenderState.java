package net.feelix.stagecoach.client;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.Map;

/** Overrides GeckoLib's add/has/getDataMap together so writes and reads share one map. */
public class StagecoachRenderState extends BlockEntityRenderState implements GeoRenderState {
    private final Map<DataTicket<?>, Object> data = new Reference2ObjectOpenHashMap<>();

    @Override
    public Map<DataTicket<?>, Object> getDataMap() {
        return data;
    }

    @Override
    public <D> void addGeckolibData(DataTicket<D> ticket, D value) {
        data.put(ticket, value);
    }

    @Override
    public boolean hasGeckolibData(DataTicket<?> ticket) {
        return data.containsKey(ticket);
    }
}
