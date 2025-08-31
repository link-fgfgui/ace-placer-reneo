package net.clayborn.accurateblockplacement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.util.Lazy;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@Mod(value = AcePlacerClient.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AcePlacerClient.MODID, value = Dist.CLIENT)
public class AcePlacerClient {
    public static final String MODID = "aceplacer";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Lazy<KeyMapping> keybind = Lazy.of(() -> new KeyMapping(
            "net.clayborn.accurateblockplacement.togglevanillaplacement", // Will be localized using this translation key
            InputConstants.Type.KEYSYM,
            -1,
            "Accurate Block Placement"
    ));
    public static boolean disableNormalItemUse = false;
    public static boolean isAccurateBlockPlacementEnabled = true;

    @SubscribeEvent
    public static void registerBindings(RegisterKeyMappingsEvent event) {
        event.register(keybind.get());
    }

    @SubscribeEvent
    public static void onClientTickEnd(ClientTickEvent.Post event) {
        while (keybind.get().consumeClick()) {
            isAccurateBlockPlacementEnabled = !isAccurateBlockPlacementEnabled;
            final MutableComponent message;
            if (isAccurateBlockPlacementEnabled) {
                message = Component.translatable("net.clayborn.accurateblockplacement.modplacementmodemessage");
            } else {
                message = Component.translatable("net.clayborn.accurateblockplacement.vanillaplacementmodemessage");
            }
            Minecraft.getInstance().gui.getChat().addMessage(message);
        }
    }
}
