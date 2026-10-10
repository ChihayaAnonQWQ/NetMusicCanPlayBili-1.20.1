package com.zhongbai233.net_music_can_play_bili.block;

import com.zhongbai233.net_music_can_play_bili.blockentity.LiveStreamerBlockEntity;
import com.zhongbai233.net_music_can_play_bili.client.LiveStreamerClientHooks;
import com.zhongbai233.net_music_can_play_bili.init.ModBlockEntities;
import com.zhongbai233.net_music_can_play_bili.init.ModItems;
import com.zhongbai233.net_music_can_play_bili.link.LinkHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import com.zhongbai233.net_music_can_play_bili.link.AudioLinkIndex;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** 直播机方块：机柜造型，右键打开控制界面，可与音响、投影仪链接。 */
public class LiveStreamerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty PLAYING = BooleanProperty.create("playing");

    public LiveStreamerBlock(BlockBehaviour.Properties properties) {
        super(properties.sound(SoundType.METAL)
                .strength(1.5F));
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(PLAYING, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LiveStreamerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.LIVE_STREAMER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> LiveStreamerBlockEntity.tick(
                tickLevel, pos, tickState, (LiveStreamerBlockEntity) blockEntity);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && (movedByPiston || !state.is(newState.getBlock()))) {
            AudioLinkIndex.removePlaybackSource(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * 方块右键交互。
     *
     * <p><b>1.20.1 移植说明</b>：1.21.1（1.20.5+）的方块交互被拆成
     * {@code useItemOn(ItemStack, ...) -> ItemInteractionResult} 与
     * {@code useWithoutItem(...) -> InteractionResult} 两个入口；1.20.1 <b>只有</b>
     * {@code use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult) -> InteractionResult}
     * 一个入口。故这里把两个方法合并：原 {@code useItemOn} 里返回
     * {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}（即"交给 useWithoutItem"）的分支，
     * 直接展开为原 {@code useWithoutItem} 的行为。</p>
     * <p>本方块两条路都归结为「打开直播机界面」，故合并后只有一个出口。</p>
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.OFF_HAND) {
            // 原 useItemOn 对副手返回 PASS_TO_DEFAULT → 原版随后调用 useWithoutItem。
            if (level.isClientSide()) {
                LiveStreamerClientHooks.openLiveStreamerScreen(pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        // 手持链接物品右键 → 存储连接目标到物品 NBT，与现代化唱片机一致
        if (stack.getItem() == ModItems.SPEAKER.get()) {
            if (level.isClientSide()) {
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            LinkHelper.writeLinkToItem(stack, pos);
            player.sendSystemMessage(Component.translatable(
                    "message.net_music_can_play_bili.speaker.item_linked",
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GOLD));
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (stack.getItem() == ModItems.VIDEO_PROJECTOR.get()) {
            if (level.isClientSide()) {
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            LinkHelper.writeLinkToItem(stack, pos);
            VideoProjectorBlock.writeLinkedBlockEntityData(stack, pos);
            player.sendSystemMessage(Component.translatable(
                    "message.net_music_can_play_bili.video_projector.item_linked",
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GOLD));
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (stack.getItem() == ModItems.CONTROL_CONSOLE.get()) {
            if (level.isClientSide()) {
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
                LinkHelper.writeControlConsoleLinkToItem(stack, pos, level.dimension().location().toString(),
                    LinkHelper.ControlConsoleSourceKind.LIVE_STREAMER);
            player.sendSystemMessage(Component.translatable(
                    "message.net_music_can_play_bili.control_console.item_linked",
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GOLD));
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (level.isClientSide()) {
            LiveStreamerClientHooks.openLiveStreamerScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        if (level instanceof Level realLevel && !realLevel.isClientSide()
                && realLevel.getBlockEntity(pos) instanceof LiveStreamerBlockEntity streamer) {
            streamer.stopForBlockRemoval();
        }
        super.destroy(level, pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLAYING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

}
