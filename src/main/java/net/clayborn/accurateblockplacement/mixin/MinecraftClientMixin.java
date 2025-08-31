package net.clayborn.accurateblockplacement.mixin;

import net.clayborn.accurateblockplacement.AcePlacerClient;
import net.clayborn.accurateblockplacement.util.IMinecraftClientAccessor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin implements IMinecraftClientAccessor {

    @Shadow
    private int rightClickDelay;

    @Shadow
    protected abstract void startUseItem();

    @Override
    public void accurateblockplacement_StartUseItemBypassDisable() {
        boolean oldValue = AcePlacerClient.disableNormalItemUse;
        AcePlacerClient.disableNormalItemUse = false;
        startUseItem();
        AcePlacerClient.disableNormalItemUse = oldValue;
    }

    @Inject(
            method = "startUseItem()V",
            at = @At("HEAD"),
            cancellable = true
    )
    void OnDoItemUse(CallbackInfo info) {
        if (AcePlacerClient.disableNormalItemUse) {
            info.cancel();
        }
    }

    @Override
    public int accurateblockplacement_GetRightClickDelay() {
        return rightClickDelay;
    }
}
