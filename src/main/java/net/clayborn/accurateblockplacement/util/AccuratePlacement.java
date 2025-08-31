package net.clayborn.accurateblockplacement.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.clayborn.accurateblockplacement.AcePlacerClient;
import net.clayborn.accurateblockplacement.mixin.KeyBindingAccessor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class AccuratePlacement {

    private static final String itemUseMethodName;
    private static final String blockActivateMethodName;
    private static final LoadingCache<Item, Boolean> itemCache;
    private static final LoadingCache<Block, Boolean> blockCache;
    private static final Logger logger = LoggerFactory.getLogger(AccuratePlacement.class);

    static {
        Method[] methods = Item.class.getMethods();

        String targetMethod = null;

        for (Method method : methods) {
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 3 && types[0] == Level.class && types[1] == Player.class && types[2] == InteractionHand.class) {
                targetMethod = method.getName();
                break;
            }
        }

        itemUseMethodName = targetMethod;

        if (itemUseMethodName == null) {
            logger.error("Could not find item use method");
        }

        itemCache = CacheBuilder.newBuilder().maximumSize(256).build(new CacheLoader<>() {
            @Override
            public @NotNull Boolean load(@NotNull Item item) {
                if (itemUseMethodName == null) return false;

                try {
                    return !item.getClass().getMethod(itemUseMethodName, Level.class, Player.class, InteractionHand.class).getDeclaringClass().equals(Item.class);
                } catch (Exception e) {
                    return false;
                }
            }
        });

        // now check for block activation methods
        methods = BlockBehaviour.class.getDeclaredMethods();
        targetMethod = null;

        for (Method method : methods) {
            Class<?>[] types = method.getParameterTypes();

            if (types.length == 5 && types[0] == BlockState.class && types[1] == Level.class
                    && types[2] == BlockPos.class && types[3] == Player.class
                    && types[4] == BlockHitResult.class) {
                targetMethod = method.getName();
                break;
            }
        }

        blockActivateMethodName = targetMethod;

        if (blockActivateMethodName == null) {
            logger.error("Could not find block activate method");
        }

        blockCache = CacheBuilder.newBuilder().maximumSize(256).build(new CacheLoader<>() {
            @Override
            public @NotNull Boolean load(@NotNull Block block) {
                if (blockActivateMethodName == null) return false;

                try {
                    return !block.getClass().getDeclaredMethod(blockActivateMethodName, BlockState.class, Level.class, BlockPos.class, Player.class, BlockHitResult.class).getDeclaringClass().equals(BlockBehaviour.class);
                } catch (Exception e) {
                    return false;
                }
            }
        });
    }

    private final ArrayList<HitResult> backFillList = new ArrayList<>();
    private BlockPos lastSeenBlockPos = null;
    private BlockPos lastPlacedBlockPos = null;
    private Vec3 lastPlayerPlacedBlockPos = null;
    private Boolean autoRepeatWaitingOnCooldown = true;
    private Vec3 lastFreshPressMouseRatio = null;
    private Item lastItemInUse = null;
    private InteractionHand handOfCurrentItemInUse;

    private static boolean doesItemHaveOverriddenUseMethod(Item item) {
        if (itemUseMethodName == null) return false;

        try {
            return itemCache.get(item);
        } catch (ExecutionException e) {
            return false;
        }
    }

    private static Boolean isBlockActivatable(Block block) {
        if (blockActivateMethodName == null) return false;

        try {
            return blockCache.get(block);
        } catch (ExecutionException e) {
            return false;
        }
    }

    public void update() {
        if (!AcePlacerClient.isAccurateBlockPlacementEnabled) {
            AcePlacerClient.disableNormalItemUse = false;
            this.lastSeenBlockPos = null;
            this.lastPlacedBlockPos = null;
            this.lastPlayerPlacedBlockPos = null;
            this.autoRepeatWaitingOnCooldown = true;
            this.backFillList.clear();
            this.lastFreshPressMouseRatio = null;
            this.lastItemInUse = null;
            return;
        }

        Minecraft client = Minecraft.getInstance();

        if (client == null || client.options == null || client.options.keyUse == null || client.hitResult == null
                || client.player == null || client.level == null || client.mouseHandler == null || client.getWindow() == null) {
            return;
        }

        tryPlace(client);
    }

    private void tryPlace(Minecraft client) {
        AcePlacerClient.disableNormalItemUse = false;

        final Player player = client.player;
        if (player == null) return;

        Item currentItem = this.getItemInUse(player);

        final boolean freshKeyPress = ((KeyBindingAccessor) client.options.keyUse).getClickCount() > 0;

        if (freshKeyPress) {
            freshKeyPress(client, currentItem);
        }

        if (!isPlacementItem(currentItem) || isTargetingSomethingElse(client)) return;

        InteractionHand otherHand = this.handOfCurrentItemInUse == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherHandItemStack = player.getItemInHand(otherHand);

        if (isInteractingWithOtherHand(player, otherHandItemStack)) return;

        BlockHitResult blockHitResult = (BlockHitResult) client.hitResult;
        if (blockHitResult == null) return;

        BlockPos blockHitPos = blockHitResult.getBlockPos();

        Level world = client.level;
        if (world == null) return;

        Block targetBlock = world.getBlockState(blockHitPos).getBlock();

        if (isBlockActivatable(targetBlock) && !(targetBlock instanceof StairBlock) && !player.isShiftKeyDown()
                || !freshKeyPress && !client.options.keyUse.isDown()) return;

        AcePlacerClient.disableNormalItemUse = true;

        BlockPlaceContext targetPlacement = new BlockPlaceContext(new UseOnContext(player, this.handOfCurrentItemInUse, blockHitResult));
        Block oldBlock = world.getBlockState(targetPlacement.getClickedPos()).getBlock();

        double facingAxisPlayerPos = 0.0;
        double facingAxisPlayerLastPos = 0.0;
        double facingAxisLastPlacedPos = 0.0;

        if (this.lastPlacedBlockPos != null && this.lastPlayerPlacedBlockPos != null) {
            Direction.Axis axis = targetPlacement.getClickedFace().getAxis();

            facingAxisPlayerPos = player.position().get(axis);
            facingAxisPlayerLastPos = this.lastPlayerPlacedBlockPos.get(axis);
            facingAxisLastPlacedPos = new Vec3(this.lastPlacedBlockPos.getX(), this.lastPlacedBlockPos.getY(), this.lastPlacedBlockPos.getZ()).get(axis);

            if (targetPlacement.getClickedFace().getName().equals("west") || targetPlacement.getClickedFace().getName().equals("north")) {
                ++facingAxisLastPlacedPos;
            }
        }

        Vec3 currentMouseRatio = null;

        if (client.getWindow().getWidth() > 0 && client.getWindow().getHeight() > 0) {
            currentMouseRatio = new Vec3(
                    client.mouseHandler.xpos() / (double) client.getWindow().getWidth(),
                    client.mouseHandler.ypos() / (double) client.getWindow().getHeight(),
                    0.0
            );
        }

        IMinecraftClientAccessor clientAccessor = (IMinecraftClientAccessor) client;

        boolean isPlacementTargetFresh =
                ((this.lastSeenBlockPos == null || !this.lastSeenBlockPos.equals(blockHitPos))
                        && (this.lastPlacedBlockPos == null || !this.lastPlacedBlockPos.equals(blockHitPos))
                ) || (this.lastPlacedBlockPos != null
                        && this.lastPlayerPlacedBlockPos != null
                        && this.lastPlacedBlockPos.equals(blockHitPos)
                        && Math.abs(facingAxisPlayerLastPos - facingAxisPlayerPos) >= 0.99
                        && Math.abs(facingAxisPlayerLastPos - facingAxisLastPlacedPos) < Math.abs(facingAxisPlayerPos - facingAxisLastPlacedPos)
                );

        boolean hasMouseMoved = currentMouseRatio != null && this.lastFreshPressMouseRatio != null && this.lastFreshPressMouseRatio.distanceTo(currentMouseRatio) >= 0.1;
        boolean isOnCooldown = this.autoRepeatWaitingOnCooldown && clientAccessor.accurateblockplacement_GetRightClickDelay() > 0 && !hasMouseMoved;

        if (this.lastItemInUse != currentItem) {
            this.lastSeenBlockPos = blockHitResult.getBlockPos();
            return;
        }

        if (!freshKeyPress && (!isPlacementTargetFresh || isOnCooldown)) {
            if (isPlacementTargetFresh) {
                this.backFillList.add(client.hitResult);
            }

            this.lastSeenBlockPos = blockHitResult.getBlockPos();
            return;
        }

        if (this.autoRepeatWaitingOnCooldown && !freshKeyPress) {
            this.autoRepeatWaitingOnCooldown = false;
            HitResult currentHitResult = client.hitResult;

            for (HitResult prevHitResult : this.backFillList) {
                client.hitResult = prevHitResult;
                clientAccessor.accurateblockplacement_StartUseItemBypassDisable();
            }

            this.backFillList.clear();
            client.hitResult = currentHitResult;
        }

        for (boolean runOnceFlag = !freshKeyPress; runOnceFlag || client.options.keyUse.consumeClick(); runOnceFlag = false) {
            clientAccessor.accurateblockplacement_StartUseItemBypassDisable();

            if (!oldBlock.equals(world.getBlockState(targetPlacement.getClickedPos()).getBlock())) {
                this.lastPlacedBlockPos = targetPlacement.getClickedPos();

                if (this.lastPlayerPlacedBlockPos == null) {
                    this.lastPlayerPlacedBlockPos = player.position();
                } else {
                    Vec3 pos = Vec3.atLowerCornerOf(targetPlacement.getClickedFace().getNormal());
                    Vec3 summedLastPlayerPos = this.lastPlayerPlacedBlockPos.add(pos);

                    this.lastPlayerPlacedBlockPos = switch (targetPlacement.getClickedFace().getAxis()) {
                        case X -> new Vec3(summedLastPlayerPos.x, player.position().y, player.position().z);
                        case Y -> new Vec3(player.position().x, summedLastPlayerPos.y, player.position().z);
                        case Z -> new Vec3(player.position().x, player.position().y, summedLastPlayerPos.z);
                    };
                }
            }
        }

        this.lastSeenBlockPos = blockHitResult.getBlockPos();
    }

    private void freshKeyPress(Minecraft client, Item currentItem) {
        this.lastSeenBlockPos = null;
        this.lastPlacedBlockPos = null;
        this.lastPlayerPlacedBlockPos = null;
        this.autoRepeatWaitingOnCooldown = true;
        this.backFillList.clear();

        if (client.getWindow().getWidth() > 0 && client.getWindow().getHeight() > 0) {
            this.lastFreshPressMouseRatio = new Vec3(
                    client.mouseHandler.xpos() / (double) client.getWindow().getWidth(),
                    client.mouseHandler.ypos() / (double) client.getWindow().getHeight(),
                    0.0
            );
        } else {
            this.lastFreshPressMouseRatio = null;
        }

        this.lastItemInUse = currentItem;
    }

    public Item getItemInUse(Player player) {
        for (InteractionHand thisHand : InteractionHand.values()) {
            ItemStack itemInHand = player.getItemInHand(thisHand);

            if (!itemInHand.isEmpty()) {
                this.handOfCurrentItemInUse = thisHand;
                return itemInHand.getItem();
            }
        }

        return null;
    }

    public boolean isPlacementItem(Item currentItem) {
        return (currentItem instanceof BlockItem || currentItem instanceof DiggerItem)
                && (!currentItem.components().has(DataComponents.FOOD) || currentItem instanceof ItemNameBlockItem)
                && !doesItemHaveOverriddenUseMethod(currentItem);
    }

    private boolean isInteractingWithOtherHand(Player player, ItemStack otherHandStack) {
        return !otherHandStack.isEmpty() && (otherHandStack.has(DataComponents.FOOD)
                || doesItemHaveOverriddenUseMethod(otherHandStack.getItem())) && player.isUsingItem();
    }

    private boolean isTargetingSomethingElse(Minecraft client) {
        return client.hitResult != null && client.hitResult.getType() != HitResult.Type.BLOCK;
    }
}
